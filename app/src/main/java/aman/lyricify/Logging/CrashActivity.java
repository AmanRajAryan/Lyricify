package aman.lyricify;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class CrashActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Root layout
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(20, 20, 20, 20);
        rootLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Crash TextView
        TextView crashTextView = new TextView(this);
        crashTextView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        
        crashTextView.setPadding(10, 10, 10, 10);
        crashTextView.setMovementMethod(new ScrollingMovementMethod());
        crashTextView.setTextIsSelectable(true); // Make text selectable

        // Get stack trace from intent
        String stackTrace = getIntent().getStringExtra("stack_trace");
        if (stackTrace != null) {
            crashTextView.setText(stackTrace);
        } else {
            crashTextView.setText("No stack trace available");
        }

        // Restart button
        Button restartButton = new Button(this);
        restartButton.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        restartButton.setText("Restart App");
        restartButton.setOnClickListener(v -> restartApp());

        // Add views to layout
        rootLayout.addView(crashTextView);
        rootLayout.addView(restartButton);

        setContentView(rootLayout);
    }

    private void restartApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}