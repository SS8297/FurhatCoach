import os
import cv2 as cv
import numpy as np
import math
from feat import Detector
from feat.utils import FEAT_EMOTION_COLUMNS
import torch
from PIL import Image

detector = Detector(face_model="retinaface", landmark_model= "pfld", au_model = "xgb", emotion_model="resmasknet")


def eye_aspect_ratio(eye):

	A = math.dist(eye[1], eye[5])
	B = math.dist(eye[2], eye[4])

	C = math.dist(eye[0], eye[3])

	ear = (A + B) / (2.0 * C)

	return ear

def detect_eyes(landmarks, img, threshold):
    lm = landmarks
    eyes = np.array(lm[36:48], np.int32)

    left_eye = eyes[0:6]
    right_eye = eyes[6:12]
    ear = max(eye_aspect_ratio(left_eye), eye_aspect_ratio(right_eye))
    left_eye = left_eye.reshape((-1,1,2))
    right_eye = right_eye.reshape((-1,1,2))
    cv.polylines(img, [left_eye], True, (0, 255, 255))
    cv.polylines(img, [right_eye], True, (255, 0, 255))

    if (ear > threshold):
         return True
    else:
         return False
    
def proc_image(img, detector):
    detected_faces = detector.detect_faces(img)
    faces_detected = len(detected_faces[0])
    if ( faces_detected < 1):
        return img
    
    detected_landmarks = detector.detect_landmarks(img, detected_faces)
    assert len(detected_landmarks[0]) == faces_detected, "Number of faces and landsmarks are mismatched!"

    is_eye_open = [detect_eyes(face, img, 0.20) for face in detected_landmarks[0]]
    eye_dict = {True: "eyes open", False: "eyes closed"}

    detected_emotions = detector.detect_emotions(img, detected_faces, detected_landmarks)
    assert len(detected_emotions[0]) == faces_detected, "Number of faces and emotions are mismatched!"

    em = detected_emotions[0]
    em_labels = em.argmax(axis=1)



    for face, has_open_eyes, label in zip(detected_faces[0], (eye_dict[eyes] for eyes in is_eye_open), em_labels):
        (x0, y0, x1, y1, p) = face
        res_scale = img.shape[0]/704
        cv.rectangle(img, (int(x0), int(y0)), (int(x1), int(y1)), color = (0, 0, 255), thickness = 3)
        cv.putText(img, FEAT_EMOTION_COLUMNS[label], (int(x0)-10, int(y1+25*res_scale*1.5)), fontFace = 0, color = (0, 255, 0), thickness = 2, fontScale = res_scale)
        cv.putText(img, f"{faces_detected } face(s) found", (0, int(25*res_scale*1.5)), fontFace = 0, color = (0, 255, 0), thickness = 2, fontScale = res_scale)
        cv.putText(img, has_open_eyes, (int(x0)-10, int(y0)-10), fontFace = 0, color = (0, 255, 0), thickness = 2, fontScale = res_scale)
        cv.imshow('frame', img)
        if (not is_eye_open):
            print(f"{{\"patientState\" : \"{FEAT_EMOTION_COLUMNS[label]}\"}}")
        else:
             print("{\"patientState\" : \"eyes_open\"}")

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

