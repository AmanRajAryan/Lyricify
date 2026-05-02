package aman.lyricify;

import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.List;

public class TagEditorUIManager {
    
    private final TagEditorActivity activity;
    
    public TagEditorUIManager(TagEditorActivity activity) {
        this.activity = activity;
    }
    
    public void showAddCustomFieldDialog(List<TagEditorActivity.CustomField> customFields, Runnable updateCallback) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_add_custom_field, null);
        EditText tagEditText = dialogView.findViewById(R.id.tagNameEditText);
        EditText valueEditText = dialogView.findViewById(R.id.tagValueEditText);

        new AlertDialog.Builder(activity)
                .setTitle("Add Custom Field")
                .setView(dialogView)
                .setPositiveButton(
                        "Add",
                        (dialog, which) -> {
                            String tag = tagEditText.getText().toString().trim().toUpperCase();
                            String value = valueEditText.getText().toString().trim();
                            if (!tag.isEmpty() && !value.isEmpty()) {
                                addCustomField(tag, value, activity.getTagFieldsContainer(), customFields, updateCallback);
                                updateCallback.run();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void addOrUpdateCustomField(String tag, String value, List<TagEditorActivity.CustomField> customFields, 
                                       LinearLayout extendedTagsContainer, Runnable updateCallback) {
        for (TagEditorActivity.CustomField field : customFields) {
            if (field.tag.equals(tag)) {
                field.value = value;
                field.editText.setText(value);
                updateCallback.run();
                return;
            }
        }
        addCustomField(tag, value, extendedTagsContainer, customFields, updateCallback);
        updateCallback.run();
    }

    public void addCustomField(String tag, String value, ViewGroup parentContainer, 
                               List<TagEditorActivity.CustomField> customFields, Runnable updateCallback) {
        TagEditorActivity.CustomField customField = new TagEditorActivity.CustomField();
        customField.tag = tag;
        customField.value = value;

        TextInputLayout inputLayout =
                new TextInputLayout(
                        activity, null, com.google.android.material.R.attr.textInputFilledStyle);
        LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, 0, 0, (int) (12 * activity.getResources().getDisplayMetrics().density));
        inputLayout.setLayoutParams(layoutParams);
        inputLayout.setHint(tag);
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_FILLED);
        inputLayout.setBoxBackgroundColor(0xFF1E1E1E);
        inputLayout.setHintTextColor(android.content.res.ColorStateList.valueOf(0xFFAAAAAA));
        inputLayout.setBoxCornerRadii(16, 16, 8, 8);
        inputLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        inputLayout.setEndIconDrawable(R.drawable.ic_delete);
        inputLayout.setEndIconTintList(android.content.res.ColorStateList.valueOf(0xFFEF5350));
        inputLayout.setEndIconOnClickListener(v -> showDeleteFieldConfirmation(customField, customFields, updateCallback));

        TextInputEditText editText = new TextInputEditText(inputLayout.getContext());
        editText.setText(value);
        editText.setTextColor(0xFFFFFFFF);
        editText.setTextSize(16);
        editText.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        updateCallback.run();
                    }
                });

        inputLayout.addView(editText);
        customField.editText = editText;
        customField.layout = inputLayout;

        parentContainer.addView(inputLayout);
        customFields.add(customField);
    }

    private void showDeleteFieldConfirmation(TagEditorActivity.CustomField field, 
                                            List<TagEditorActivity.CustomField> customFields, 
                                            Runnable updateCallback) {
        new AlertDialog.Builder(activity)
                .setTitle("Delete")
                .setMessage("Delete " + field.tag + "?")
                .setPositiveButton(
                        "Delete",
                        (d, w) -> {
                            ((ViewGroup) field.layout.getParent()).removeView(field.layout);
                            customFields.remove(field);
                            updateCallback.run();
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }
}