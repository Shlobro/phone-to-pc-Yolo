package com.example.yoloandfeedonpcandcameraonphone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class DetectionOverlayView extends View {
    private Paint paint;
    private List<DetectionBox> detectionBoxes;

    public DetectionOverlayView(Context context) {
        super(context);
        init();
    }

    public DetectionOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        detectionBoxes = new ArrayList<>();
    }

    public void setDetections(List<DetectionBox> detections) {
        this.detectionBoxes = detections != null ? detections : new ArrayList<>();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        for (DetectionBox box : detectionBoxes) {
            RectF rect = new RectF(
                box.x * getWidth(),
                box.y * getHeight(),
                (box.x + box.width) * getWidth(),
                (box.y + box.height) * getHeight()
            );
            canvas.drawRect(rect, paint);
            
            if (box.label != null && !box.label.isEmpty()) {
                Paint textPaint = new Paint();
                textPaint.setColor(Color.RED);
                textPaint.setTextSize(40f);
                canvas.drawText(box.label + " " + String.format("%.2f", box.confidence), 
                    rect.left, rect.top - 10, textPaint);
            }
        }
    }

    public static class DetectionBox {
        public float x, y, width, height;
        public String label;
        public float confidence;

        public DetectionBox(float x, float y, float width, float height, String label, float confidence) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.label = label;
            this.confidence = confidence;
        }
    }
}