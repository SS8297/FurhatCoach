import socket
import json
import time
# Import OpenCV, PyFeat, and other necessary libraries here
import os
import cv2 as cv
import numpy as np
import math
from feat import Detector
from feat.utils import FEAT_EMOTION_COLUMNS
from PIL import Image
import torch
from torch import nn

import torchvision
print(torchvision.__version__)


CLASS_LABELS = ['angry', 'disgust', 'fear', 'happy', 'neutral', 'sad', 'surprise']

# Set up a socket server to communicate with the Kotlin application
HOST, PORT = 'localhost', 9999
server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server.bind((HOST, PORT))
server.listen(1)

# Emotion cache and timestamp
last_emotion_index = -1
last_time = 0

device = (
    "cuda"
    if torch.cuda.is_available()
    else "cpu"
)
try:
    emo_model = torch.load("acc_96.8", map_location=device)
except Exception as e:
    print(f"Error loading model: {e}")
    print("Download the model and put in the src/main/python folder: https://drive.google.com/file/d/1PXsMlF6bzvowkAaSE14UWa1F5fO7E8nu/view?usp=sharing")
    exit()
detector = Detector(face_model="retinaface", landmark_model= "pfld")

def extract_features(landmarks, face):
    features = [math.dist(landmarks[33], landmark) for landmark in landmarks] + [face[2] - face[0], face[3] - face[1]]
    return features

class MyNeuralNetwork(nn.Module):
    def __init__(self, layers, dropout):
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(70, layers[0]),
            nn.LeakyReLU(),
            nn.Dropout(p = dropout[0]),
            nn.Linear(layers[0], layers[1]),
            nn.LeakyReLU(),
            nn.Dropout(p = dropout[1]),
            nn.Linear(layers[1], layers[2]),
            nn.LeakyReLU(),
            nn.Dropout(p = dropout[2]),
            nn.Linear(layers[2], layers[3]),
            nn.LeakyReLU(),
            nn.Dropout(p = dropout[3]),
            nn.Linear(layers[3], 7),
        )
    
    def forward(self, inputs):
        return self.net(inputs)

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

def proc_image(img, detector, emo_model):
    detected_faces = detector.detect_faces(img)
    faces_detected = len(detected_faces[0])
    if ( faces_detected < 1):
        return img
    
    detected_landmarks = detector.detect_landmarks(img, detected_faces)
    assert len(detected_landmarks[0]) == faces_detected, "Number of faces and landsmarks are mismatched!"

    is_eye_open = [detect_eyes(face, img, 0.20) for face in detected_landmarks[0]]
    eye_dict = {True: "eyes_opened", False: "eyes_closed"}

    device = (
        "cuda"
        if torch.cuda.is_available()
        else "cpu"
    )

    features = [torch.tensor(np.array(extract_features(*object)).astype(np.float32)).to(device) for object in zip(detected_landmarks[0], detected_faces[0])]
    detected_emotions = [emo_model(facefeat).softmax(dim=0).argmax(dim=0).to("cpu") for facefeat in features]
    assert len(detected_emotions) == faces_detected, "Number of faces and emotions are mismatched!"

    for face, has_open_eyes, label in zip(detected_faces[0], (eye_dict[eyes] for eyes in is_eye_open), detected_emotions):
        (x0, y0, x1, y1, p) = face
        res_scale = img.shape[0]/704
        cv.rectangle(img, (int(x0), int(y0)), (int(x1), int(y1)), color = (0, 0, 255), thickness = 3)
        cv.putText(img, CLASS_LABELS[label], (int(x0)-10, int(y1+25*res_scale*1.5)), fontFace = 0, color = (0, 255, 0), thickness = 2, fontScale = res_scale)
        cv.putText(img, f"{faces_detected } face(s) found", (0, int(25*res_scale*1.5)), fontFace = 0, color = (0, 255, 0), thickness = 2, fontScale = res_scale)
        cv.putText(img, has_open_eyes, (int(x0)-10, int(y0)-10), fontFace = 0, color = (0, 255, 0), thickness = 2, fontScale = res_scale)
        cv.imshow('frame', img)
        if (not is_eye_open):
            print(FEAT_EMOTION_COLUMNS[label])
        else:
             print("eyes_open")

        #JSON
        # if (not is_eye_open):
        #     print(f"{{\"patientState\" : \"{FEAT_EMOTION_COLUMNS[label]}\"}}")
        # else:
        #      print("{\"patientState\" : \"eyes_open\"}")

cap = cv.VideoCapture(0)
if not cap.isOpened():
    print("Cannot open camera")
    exit()

while True:
    try:
        conn, addr = server.accept()
        try:
            while True:
                data = conn.recv(1024)
                print("Received data:", data.decode())
                if not data:
                    break

                ret, frame = cap.read()

                if not ret:
                    print("Can't receive frame (steam end?).Exiting ...")
                    break

                emotion = proc_image(frame, detector, emo_model)
                response = json.dumps({'patientState': emotion})
                print("Sending response:", response)
                conn.sendall(response.encode('utf-8') + b"\n")
                conn.flush()
            cap.release()
            cv.destroyAllWindows()
        finally:
            conn.close()
    except Exception as e:
        print(f"Error: {e}")