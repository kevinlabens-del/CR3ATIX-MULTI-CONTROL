package fr.creatix.multicontrol;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Point;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;

/** Explicitly enabled by the user in Android accessibility settings. */
public class ReceiverAccessibility extends AccessibilityService {
    static volatile ReceiverAccessibility active;
    private int x, y;

    @Override protected void onServiceConnected() {
        super.onServiceConnected(); active = this;
        Point p = size(); x = p.x / 2; y = p.y / 2;
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
    @Override public void onDestroy() { active = null; super.onDestroy(); }

    private Point size() {
        Display display = ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
        Point point = new Point(); display.getRealSize(point); return point;
    }
    private void tap(int tx, int ty, long duration) {
        Path path = new Path(); path.moveTo(tx, ty);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, duration)).build();
        dispatchGesture(gesture, null, null);
    }
    void command(JSONObject msg) {
        String type = msg.optString("type");
        if (type.equals("move")) {
            Point p = size();
            x = Math.max(1, Math.min(p.x - 2, x + Math.max(-200, Math.min(200, msg.optInt("dx")))));
            y = Math.max(1, Math.min(p.y - 2, y + Math.max(-200, Math.min(200, msg.optInt("dy")))));
            // Accessibility cannot move Android's system mouse cursor. x/y represent the next gesture target.
        } else if (type.equals("click")) {
            if (msg.optString("button").equals("left")) tap(x, y, 55);
        } else if (type.equals("scroll")) {
            Path path = new Path(); path.moveTo(x, y);
            path.lineTo(x, y + (msg.optInt("steps") > 0 ? 220 : -220));
            dispatchGesture(new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 260)).build(), null, null);
        } else if (type.equals("key")) {
            String key = msg.optString("key");
            if (key.equals("esc") || key.equals("backspace")) performGlobalAction(GLOBAL_ACTION_BACK);
        } else if (type.equals("text")) {
            String value = msg.optString("text");
            if (value.length() > 2048) return;
            ClipboardManager clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Multi Control", value));
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (focused != null) focused.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                root.recycle();
            }
        }
    }
}
