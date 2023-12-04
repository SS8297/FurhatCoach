import numpy as np
import cv2 as cv
from feat import Detector
from IPython.display import Image
from feat.utils import FEAT_EMOTION_COLUMNS


detector = Detector(face_model="retinaface", au_model = "xgb", emotion_model="resmasknet")

def proc_image(img, detector):

    #img = cv.resize(img, dsize=(120, 180), interpolation=cv.INTER_CUBIC)
    detected_faces = detector.detect_faces(img)
    detected_landmarks = detector.detect_landmarks(frame, detected_faces)
    detected_emotions = detector.detect_emotions(frame, detected_faces, detected_landmarks)

    em = detected_emotions[0]
    em_labels = em.argmax(axis=1)

    for face, label in zip(detected_faces[0], em_labels):
        (x0, y0, x1, y1, p) = face
        img = cv.rectangle(img, (int(x0), int(y0)), (int(x1), int(y1)), color = (0, 0, 255), thickness = 3)
        cv.putText(img, FEAT_EMOTION_COLUMNS[label], (int(x0)-10, int(y0)-10), fontFace = 0, color = (0, 0, 255), thickness = 2, fontScale = 1)
        cv.imshow('frame', img)
        print(f"\"patientState\" : \"{FEAT_EMOTION_COLUMNS[label]}\"")
        #print(detected_landmarks)


counter = 0
cap = cv.VideoCapture(0)
if not cap.isOpened():
        print("Cannot open camera")
        exit()
while True:
    # Capture frame-by-frame
    ret, frame = cap.read()

    #if frame is read correctly ret is True
    if not ret:
        print("Can't receive frame (steam end?).Exiting ...")
        break

    # Display the resulting frame
    if (counter == 0):
        proc_image(frame, detector)
    counter += 1
    counter = counter % 8
    if cv.waitKey(1) == ord('q'):
         break

cap.release()
cv.destroyAllWindows()

