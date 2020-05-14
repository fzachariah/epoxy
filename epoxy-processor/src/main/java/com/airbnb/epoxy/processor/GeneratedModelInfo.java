package com.airbnb.epoxy.processor;

import com.airbnb.epoxy.EpoxyAttribute;
import com.airbnb.epoxy.ModelView.Size;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterSpec.Builder;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import androidx.annotation.Nullable;

import static com.airbnb.epoxy.processor.Utils.buildEpoxyException;
import static com.airbnb.epoxy.processor.Utils.isSubtypeOfType;

public abstract class GeneratedModelInfo {
  private static final String RESET_METHOD = "reset";
  public static final String GENERATED_CLASS_NAME_SUFFIX = "_";
  public static final String GENERATED_MODEL_SUFFIX = "Model" + GENERATED_CLASS_NAME_SUFFIX;

  protected TypeName superClassName;
  protected TypeElement superClassElement;
  protected TypeName parametrizedClassName;
  protected ClassName generatedClassName;
  protected TypeName boundObjectTypeName;
  protected boolean shouldGenerateModel;
  /**
   * If true, any layout classes that exist that are prefixed by the default layout are included in
   * the generated model as other layout options via a generated method for each alternate layout.
   */
  protected boolean includeOtherLayoutOptions;
  // TODO: (eli_hart 5/16/17) Sort attributes alphabetically so overloaded setters are together
  protected final List<AttributeInfo> attributeInfo = new ArrayList<>();
  protected final List<TypeVariableName> typeVariableNames = new ArrayList<>();
  protected final List<ConstructorInfo> constructors = new ArrayList<>();
  protected final Set<MethodInfo> methodsReturningClassType = new LinkedHashSet<>();
  protected final List<AttributeGroup> attributeGroups = new ArrayList<>();
  protected final List<AnnotationSpec> annotations = new ArrayList<>();

  /**
   * The info for the style builder if this is a model view annotated with @Styleable. Null
   * otherwise.
   */
  private ParisStyleAttributeInfo styleBuilderInfo;

  /**
   * An option set via {@link com.airbnb.epoxy.ModelView#autoLayout()} to have Epoxy create the
   * view programmatically
   * instead of via xml layout resource inflation.
   */
  Size layoutParams = Size.NONE;

  /**
   * The elements that influence the generation of this model.
   * eg base model class for @EpoxyModelClass, view class for @ModelView, etc
   */
  public List<Element> originatingElements() {
    if (styleBuilderInfo != null) {
      return Collections.singletonList(styleBuilderInfo.getStyleBuilderElement());
    }

    return Collections.emptyList();
  }

  /**
   * Get information about constructors of the original class so we can duplicate them in the
   * generated class and call through to super with the proper parameters
   */
  protected static List<ConstructorInfo> getClassConstructors(TypeElement classElement) {
    List<ConstructorInfo> constructors = new ArrayList<>(2);

    for (Element subElement : SynchronizationKt.getEnclosedElementsThreadSafe(classElement)) {
      if (subElement.getKind() == ElementKind.CONSTRUCTOR
          && !subElement.getModifiers().contains(Modifier.PRIVATE)) {

        ExecutableElement constructor = ((ExecutableElement) subElement);
        List<? extends VariableElement> params =
            SynchronizationKt.getParametersThreadSafe(constructor);
        constructors.add(new ConstructorInfo(subElement.getModifiers(), buildParamSpecs(params),
            constructor.isVarArgs()));
      }
    }

    return constructors;
  }

  /**
   * Get information about methods returning class type of the original class so we can duplicate
   * them in the generated class for chaining purposes
   */
  protected void collectMethodsReturningClassType(TypeElement modelClass, Types typeUtils) {
    TypeElement clazz = modelClass;
    while (true) {
      TypeMirror superclass = clazz.getSuperclass();
      SynchronizationKt.ensureLoaded(superclass);
      if (superclass.getKind() == TypeKind.NONE) break;

      for (Element subElement : SynchronizationKt.getEnclosedElementsThreadSafe(clazz)) {
        Set<Modifier> modifiers = subElement.getModifiers();
        if (subElement.getKind() == ElementKind.METHOD
            && !modifiers.contains(Modifier.PRIVATE)
            && !modifiers.contains(Modifier.FINAL)
            && !modifiers.contains(Modifier.STATIC)) {
          TypeMirror methodReturnType = ((ExecutableType) subElement.asType()).getReturnType();
          if (methodReturnType.equals(clazz.asType())
              || Utils.isSubtype(clazz.asType(), methodReturnType, typeUtils)) {
            ExecutableElement castedSubElement = ((ExecutableElement) subElement);
            List<? extends VariableElement> params =
                SynchronizationKt.getParametersThreadSafe(castedSubElement);
            String methodName = subElement.getSimpleName().toString();
            if (methodName.equals(RESET_METHOD) && params.isEmpty()) {
              continue;
            }
            boolean isEpoxyAttribute = castedSubElement.getAnnotation(EpoxyAttribute.class) != null;
            methodsReturningClassType.add(new MethodInfo(methodName, modifiers,
                buildParamSpecs(params), castedSubElement.isVarArgs(), isEpoxyAttribute,
                castedSubElement));
          }
        }
      }
      clazz = (TypeElement) typeUtils.asElement(superclass);
    }
  }

  protected static List<ParameterSpec> buildParamSpecs(List<? extends VariableElement> params) {
    List<ParameterSpec> result = new ArrayList<>();

    for (VariableElement param : params) {
      Builder builder = ParameterSpec.builder(TypeName.get(param.asType()),
          param.getSimpleName().toString());
      for (AnnotationMirror annotation : param.getAnnotationMirrors()) {
        builder.addAnnotation(AnnotationSpec.get(annotation));
      }
      result.add(builder.build());
    }

    return result;
  }

  synchronized void addAttribute(AttributeInfo attributeInfo) {
    addAttributes(Collections.singletonList(attributeInfo));
  }

  synchronized void addAttributes(Collection<AttributeInfo> attributesToAdd) {
    removeMethodIfDuplicatedBySetter(attributesToAdd);
    for (AttributeInfo info : attributesToAdd) {
      int existingIndex = attributeInfo.indexOf(info);
      if (existingIndex > -1) {
        // Don't allow duplicates.
        attributeInfo.set(existingIndex, info);
      } else {
        attributeInfo.add(info);
      }
    }
  }

  void addAttributeIfNotExists(AttributeInfo attributeToAdd) {
    if (!attributeInfo.contains(attributeToAdd)) {
      addAttribute(attributeToAdd);
    }
  }

  private void removeMethodIfDuplicatedBySetter(Collection<AttributeInfo> attributeInfos) {
    for (AttributeInfo attributeInfo : attributeInfos) {
      Iterator<MethodInfo> iterator = methodsReturningClassType.iterator();
      while (iterator.hasNext()) {
        MethodInfo methodInfo = iterator.next();
        if (methodInfo.getName().equals(attributeInfo.getFieldName())
            // checking for overloads
            && methodInfo.getParams().size() == 1
            && methodInfo.getParams().get(0).type.equals(attributeInfo.getTypeName())) {
          iterator.remove();
        }
      }
    }
  }

  TypeName getSuperClassName() {
    return superClassName;
  }

  Set<MethodInfo> getMethodsReturningClassType() {
    return methodsReturningClassType;
  }

  ClassName getGeneratedName() {
    return generatedClassName;
  }

  List<AttributeInfo> getAttributeInfo() {
    return attributeInfo;
  }

  Iterable<TypeVariableName> getTypeVariables() {
    return typeVariableNames;
  }

  TypeName getParameterizedGeneratedName() {
    return parametrizedClassName;
  }

  /**
   * Get the object type this model is typed with.
   */
  TypeName getModelType() {
    return boundObjectTypeName;
  }

  List<AnnotationSpec> getAnnotations() {
    return annotations;
  }

  @Nullable
  ParisStyleAttributeInfo getStyleBuilderInfo() {
    return styleBuilderInfo;
  }

  boolean isStyleable() {
    return getStyleBuilderInfo() != null;
  }

  void setStyleable(
      @NotNull ParisStyleAttributeInfo parisStyleAttributeInfo) {
    styleBuilderInfo = parisStyleAttributeInfo;
    addAttribute(parisStyleAttributeInfo);
  }

  boolean isProgrammaticView() {
    return isStyleable() || layoutParams != Size.NONE;
  }

  boolean hasEmptyConstructor() {
    if (constructors.isEmpty()) {
      return true;
    } else {
      for (ConstructorInfo constructor : constructors) {
        if (constructor.params.isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @return True if the super class of this generated model is also extended from a generated
   * model.
   */
  public boolean isSuperClassAlsoGenerated() {
    return isSubtypeOfType(superClassElement.asType(), "com.airbnb.epoxy.GeneratedModel<?>");
  }

  static class ConstructorInfo {
    final Set<Modifier> modifiers;
    final List<ParameterSpec> params;
    final boolean varargs;

    ConstructorInfo(Set<Modifier> modifiers, List<ParameterSpec> params, boolean varargs) {
      this.modifiers = modifiers;
      this.params = params;
      this.varargs = varargs;
    }
  }

  @Override
  public String toString() {
    return "GeneratedModelInfo{"
        + "attributeInfo=" + attributeInfo
        + ", superClassName=" + superClassName
        + '}';
  }

  void addAttributeGroup(String groupName, List<AttributeInfo> attributes)
      throws EpoxyProcessorException {

    AttributeInfo defaultAttribute = null;
    for (AttributeInfo attribute : attributes) {

      if (attribute.isRequired()
          || (attribute.getCodeToSetDefault().isEmpty() && !hasDefaultKotlinValue(attribute))) {
        continue;
      }

      boolean hasSetExplicitDefault =
          defaultAttribute != null && hasExplicitDefault(defaultAttribute);

      if (hasSetExplicitDefault && hasExplicitDefault(attribute)) {
        throw buildEpoxyException(
            "Only one default value can exist for a group of attributes: " + attributes);
      }

      // Have the one explicit default value in the group trump everything else.
      if (hasSetExplicitDefault) {
        continue;
      }

      // If only implicit
      // defaults exist, have a null default trump default primitives. This makes it so if there
      // is a nullable object and a primitive in a group, the default value will be to null out the
      // object.
      if (defaultAttribute == null
          || hasExplicitDefault(attribute)
          || attribute.hasSetNullability()) {
        defaultAttribute = attribute;
      }
    }

    AttributeGroup group = new AttributeGroup(groupName, attributes, defaultAttribute);
    attributeGroups.add(group);
    for (AttributeInfo attribute : attributes) {
      attribute.setAttributeGroup(group);
    }
  }

  private static boolean hasDefaultKotlinValue(AttributeInfo attribute) {
    if (attribute instanceof ViewAttributeInfo) {
      return ((ViewAttributeInfo) attribute).getHasDefaultKotlinValue();
    }

    return false;
  }

  private static boolean hasExplicitDefault(AttributeInfo attribute) {
    if (attribute.getCodeToSetDefault().getExplicit() != null) {
      return true;
    }

    if (attribute instanceof ViewAttributeInfo) {
      return ((ViewAttributeInfo) attribute).getHasDefaultKotlinValue();
    }

    return false;
  }

  public static class AttributeGroup {
    final String name;
    final List<AttributeInfo> attributes;
    final boolean isRequired;
    final AttributeInfo defaultAttribute;

    AttributeGroup(String groupName, List<AttributeInfo> attributes,
        AttributeInfo defaultAttribute) throws EpoxyProcessorException {
      if (attributes.isEmpty()) {
        throw buildEpoxyException("Attributes cannot be empty");
      }

      if (defaultAttribute != null
          && defaultAttribute.getCodeToSetDefault().isEmpty()
          && !hasDefaultKotlinValue(defaultAttribute)) {
        throw buildEpoxyException("Default attribute has no default code");
      }

      this.defaultAttribute = defaultAttribute;
      isRequired = defaultAttribute == null;
      this.name = groupName;
      this.attributes = new ArrayList<>(attributes);
    }
  }
}
