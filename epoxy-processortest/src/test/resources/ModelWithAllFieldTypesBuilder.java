package com.airbnb.epoxy;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import java.lang.Boolean;
import java.lang.Byte;
import java.lang.CharSequence;
import java.lang.Character;
import java.lang.Double;
import java.lang.Float;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Number;
import java.lang.Object;
import java.lang.Short;
import java.lang.String;
import java.util.List;

public interface ModelWithAllFieldTypesBuilder {
  ModelWithAllFieldTypesBuilder onBind(
      OnModelBoundListener<ModelWithAllFieldTypes_, Object> listener);

  ModelWithAllFieldTypesBuilder onUnbind(
      OnModelUnboundListener<ModelWithAllFieldTypes_, Object> listener);

  ModelWithAllFieldTypesBuilder onVisibilityStateChanged(
      OnModelVisibilityStateChangedListener<ModelWithAllFieldTypes_, Object> listener);

  ModelWithAllFieldTypesBuilder onVisibilityChanged(
      OnModelVisibilityChangedListener<ModelWithAllFieldTypes_, Object> listener);

  ModelWithAllFieldTypesBuilder valueBoolean(boolean valueBoolean);

  ModelWithAllFieldTypesBuilder valueBooleanWrapper(Boolean valueBooleanWrapper);

  ModelWithAllFieldTypesBuilder valueByteWrapper(Byte valueByteWrapper);

  ModelWithAllFieldTypesBuilder valueChar(char valueChar);

  ModelWithAllFieldTypesBuilder valueCharacter(Character valueCharacter);

  ModelWithAllFieldTypesBuilder valueDouble(double valueDouble);

  ModelWithAllFieldTypesBuilder valueDoubleWrapper(Double valueDoubleWrapper);

  ModelWithAllFieldTypesBuilder valueFloat(float valueFloat);

  ModelWithAllFieldTypesBuilder valueFloatWrapper(Float valueFloatWrapper);

  ModelWithAllFieldTypesBuilder valueInt(int valueInt);

  ModelWithAllFieldTypesBuilder valueIntArray(int[] valueIntArray);

  ModelWithAllFieldTypesBuilder valueInteger(Integer valueInteger);

  ModelWithAllFieldTypesBuilder valueList(List<String> valueList);

  ModelWithAllFieldTypesBuilder valueLong(long valueLong);

  ModelWithAllFieldTypesBuilder valueLongWrapper(Long valueLongWrapper);

  ModelWithAllFieldTypesBuilder valueObject(Object valueObject);

  ModelWithAllFieldTypesBuilder valueObjectArray(Object[] valueObjectArray);

  ModelWithAllFieldTypesBuilder valueShort(short valueShort);

  ModelWithAllFieldTypesBuilder valueShortWrapper(Short valueShortWrapper);

  ModelWithAllFieldTypesBuilder valueString(String valueString);

  ModelWithAllFieldTypesBuilder valuebByte(byte valuebByte);

  ModelWithAllFieldTypesBuilder id(long id);

  ModelWithAllFieldTypesBuilder id(@Nullable Number... arg0);

  ModelWithAllFieldTypesBuilder id(long id1, long id2);

  ModelWithAllFieldTypesBuilder id(@Nullable CharSequence arg0);

  ModelWithAllFieldTypesBuilder id(@Nullable CharSequence arg0, @Nullable CharSequence... arg1);

  ModelWithAllFieldTypesBuilder id(@Nullable CharSequence arg0, long arg1);

  ModelWithAllFieldTypesBuilder layout(@LayoutRes int arg0);

  ModelWithAllFieldTypesBuilder spanSizeOverride(
      @Nullable EpoxyModel.SpanSizeOverrideCallback arg0);
}