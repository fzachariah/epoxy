package com.airbnb.epoxy;

import android.view.View;
import androidx.annotation.Dimension;
import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;
import java.lang.Boolean;
import java.lang.CharSequence;
import java.lang.Integer;
import java.lang.Number;
import java.lang.Object;
import java.lang.String;
import java.util.List;

public interface TestManyTypesViewModelBuilder {
  TestManyTypesViewModelBuilder onBind(
      OnModelBoundListener<TestManyTypesViewModel_, TestManyTypesView> listener);

  TestManyTypesViewModelBuilder onUnbind(
      OnModelUnboundListener<TestManyTypesViewModel_, TestManyTypesView> listener);

  TestManyTypesViewModelBuilder onVisibilityStateChanged(
      OnModelVisibilityStateChangedListener<TestManyTypesViewModel_, TestManyTypesView> listener);

  TestManyTypesViewModelBuilder onVisibilityChanged(
      OnModelVisibilityChangedListener<TestManyTypesViewModel_, TestManyTypesView> listener);

  TestManyTypesViewModelBuilder arrayValue(@NonNull String[] arrayValue);

  TestManyTypesViewModelBuilder boolValue(boolean boolValue);

  TestManyTypesViewModelBuilder booleanValue(@NonNull Boolean booleanValue);

  TestManyTypesViewModelBuilder clickListener(
      @NonNull final OnModelClickListener<TestManyTypesViewModel_, TestManyTypesView> clickListener);

  TestManyTypesViewModelBuilder clickListener(@NonNull View.OnClickListener clickListener);

  TestManyTypesViewModelBuilder intValueWithAnnotation(@StringRes int intValueWithAnnotation);

  TestManyTypesViewModelBuilder intValueWithDimenTypeAnnotation(
      @Dimension(unit = 0) int intValueWithDimenTypeAnnotation);

  TestManyTypesViewModelBuilder intValueWithRangeAnnotation(
      @IntRange(from = 0, to = 200) int intValueWithRangeAnnotation);

  TestManyTypesViewModelBuilder intValue(int intValue);

  TestManyTypesViewModelBuilder intWithMultipleAnnotations(
      @IntRange(from = 0, to = 200) @Dimension(unit = 0) int intWithMultipleAnnotations);

  TestManyTypesViewModelBuilder integerValue(@NonNull Integer integerValue);

  TestManyTypesViewModelBuilder listValue(@NonNull List<String> listValue);

  TestManyTypesViewModelBuilder nullableStringValue(@Nullable String nullableStringValue);

  TestManyTypesViewModelBuilder stringValue(@NonNull String stringValue);

  TestManyTypesViewModelBuilder title(@Nullable CharSequence title);

  TestManyTypesViewModelBuilder title(@StringRes int stringRes);

  TestManyTypesViewModelBuilder title(@StringRes int stringRes, Object... formatArgs);

  TestManyTypesViewModelBuilder titleQuantityRes(@PluralsRes int pluralRes, int quantity,
      Object... formatArgs);

  TestManyTypesViewModelBuilder id(long id);

  TestManyTypesViewModelBuilder id(@Nullable Number... arg0);

  TestManyTypesViewModelBuilder id(long id1, long id2);

  TestManyTypesViewModelBuilder id(@Nullable CharSequence arg0);

  TestManyTypesViewModelBuilder id(@Nullable CharSequence arg0, @Nullable CharSequence... arg1);

  TestManyTypesViewModelBuilder id(@Nullable CharSequence arg0, long arg1);

  TestManyTypesViewModelBuilder layout(@LayoutRes int arg0);

  TestManyTypesViewModelBuilder spanSizeOverride(
      @Nullable EpoxyModel.SpanSizeOverrideCallback arg0);
}