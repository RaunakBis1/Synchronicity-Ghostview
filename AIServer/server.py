from flask import Flask, request, jsonify
from ultralytics import YOLO
import cv2
import numpy as np

app = Flask(__name__)

@app.route("/")
def home():
    return {
        "status": "running",
        "service": "GhostView AI"
    }

# -------------------------
# YOLO
# -------------------------

model = YOLO("yolov8n.pt")
PHONE_CLASS_ID = 67

# -------------------------
# FACE DETECTION
# -------------------------

import mediapipe as mp
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

base_options = python.BaseOptions(model_asset_path='blaze_face_short_range.tflite')
options = vision.FaceDetectorOptions(base_options=base_options)
face_detector = vision.FaceDetector.create_from_options(options)

# -------------------------
# API
# -------------------------

@app.route("/detect", methods=["POST"])
def detect():
    if "image" not in request.files:
        return jsonify({"error": "No image"})

    file = request.files["image"]
    image_bytes = file.read()
    np_array = np.frombuffer(image_bytes, np.uint8)
    frame = cv2.imdecode(np_array, cv2.IMREAD_COLOR)

    # PHONE DETECTION
    phone_detected = False
    results = model(frame, verbose=False)
    for result in results:
        for box in result.boxes:
            class_id = int(box.cls[0])
            confidence = float(box.conf[0])
            if class_id == PHONE_CLASS_ID and confidence > 0.40:
                x1, y1, x2, y2 = map(int, box.xyxy[0])
                width = x2 - x1
                height = y2 - y1

                # Ignore tiny false detections
                if width < 40 or height < 40:
                    continue

                phone_detected = True

                cv2.rectangle(
                    frame,
                    (x1, y1),
                    (x2, y2),
                    (0, 255, 0),
                    2
                )

                label = f"Phone {confidence:.2f}"
                cv2.putText(
                    frame,
                    label,
                    (x1, y1 - 10),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.6,
                    (0, 255, 0),
                    2
                )

    # FACE DETECTION WITH MEDIAPIPE
    rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
    detection_result = face_detector.detect(mp_image)
    face_count = len(detection_result.detections)
    profiles = [] # No longer used directly as Mediapipe detects both

    # BLACKOUT LOGIC
    blackout = False
    reason = "safe"

    if phone_detected:
        blackout = True
        reason = "phone_detected"
    elif face_count > 1:
        blackout = True
        reason = "multiple_faces"
    elif face_count == 0:
        blackout = True
        reason = "no_face_detected"

    return jsonify({
        "blackout": blackout,
        "reason": reason,
        "phone_detected": phone_detected,
        "face_count": face_count
    })

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)

