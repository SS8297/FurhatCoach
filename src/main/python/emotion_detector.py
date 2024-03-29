import socket
import json
import cv2 as cv
import numpy as np
import math
from feat import Detector
from feat.utils import FEAT_EMOTION_COLUMNS
import torch
from torch import nn
import threading
import time

running = True
last_emotion = None

HOST, PORT = 'localhost', 9999
CLASS_LABELS = ['angry', 'disgust', 'fear', 'happy', 'neutral', 'sad', 'surprise']
UPDATE_INTERVAL = 7

device = "cuda" if torch.cuda.is_available() else "cpu"

class MyNeuralNetwork(nn.Module):
    def __init__(self, layers, dropout):
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(70, layers[0]), nn.LeakyReLU(), nn.Dropout(p=dropout[0]),
            nn.Linear(layers[0], layers[1]), nn.LeakyReLU(), nn.Dropout(p=dropout[1]),
            nn.Linear(layers[1], layers[2]), nn.LeakyReLU(), nn.Dropout(p=dropout[2]),
            nn.Linear(layers[2], layers[3]), nn.LeakyReLU(), nn.Dropout(p=dropout[3]),
            nn.Linear(layers[3], 7)
        )

    def forward(self, inputs):
        return self.net(inputs)

def extract_features(landmarks, face):
    return [math.dist(landmarks[33], landmark) for landmark in landmarks] + [face[2] - face[0], face[3] - face[1]]

def eye_aspect_ratio(eye):
    A = math.dist(eye[1], eye[5])
    B = math.dist(eye[2], eye[4])
    C = math.dist(eye[0], eye[3])
    return (A + B) / (2.0 * C)

def detect_eyes(landmarks, img, threshold):
    eyes = np.array(landmarks[36:48], np.int32)
    left_eye, right_eye = eyes[:6], eyes[6:]
    ear = max(eye_aspect_ratio(left_eye), eye_aspect_ratio(right_eye))
    cv.polylines(img, [left_eye.reshape((-1,1,2))], True, (0, 255, 255))
    cv.polylines(img, [right_eye.reshape((-1,1,2))], True, (255, 0, 255))
    return ear > threshold

def proc_image(img, detector, emo_model):
    detected_faces = detector.detect_faces(img)
    if len(detected_faces[0]) < 1:
        return "no face"

    detected_landmarks = detector.detect_landmarks(img, detected_faces)
    features = [torch.tensor(np.array(extract_features(landmark, face)).astype(np.float32)).to(device)
                for landmark, face in zip(detected_landmarks[0], detected_faces[0])]

    detected_emotions = [emo_model(feature).softmax(dim=0).argmax(dim=0).item() for feature in features]
    for face, label in zip(detected_faces[0], detected_emotions):
        (x0, y0, x1, y1, p) = face
        cv.rectangle(img, (int(x0), int(y0)), (int(x1), int(y1)), (0, 0, 255), 3)
        cv.putText(img, CLASS_LABELS[label], (int(x0)-10, int(y1+30)), cv.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)

    cv.imshow('frame', img)
    emotion = FEAT_EMOTION_COLUMNS[detected_emotions[0]] if detected_emotions else "no face"
    return emotion

def camera_thread(cap, detector, emo_model):
    global running, last_emotion
    last_update_time = 0
    while running:
        ret, frame = cap.read()
        if not ret:
            print("Failed to grab frame")
            break
        current_time = time.time()
        if current_time - last_update_time >= UPDATE_INTERVAL:
            try:
                new_emotion = proc_image(frame, detector, emo_model)
                if new_emotion != last_emotion:
                    last_emotion = new_emotion
                    print(f"Emotion: {last_emotion}")
                    last_update_time = current_time
            except Exception as e:
                print(f"Error processing image: {e}")

        cv.imshow('frame', frame)
        if cv.waitKey(1) & 0xFF == ord('q'):
            break

    cap.release()
    cv.destroyAllWindows()

def socket_server_thread():
    global running, last_emotion
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.settimeout(10)
    server.bind((HOST, PORT))
    server.listen(1)

    print("Socket server started")

    while running:
        try:
            conn, addr = server.accept()
            print("Client connected")
            while running:
                data = conn.recv(1024)
                if not data:
                    break

                emotion = last_emotion if last_emotion is not None else "no data"
                response = json.dumps({'patientState': emotion})
                conn.sendall(response.encode('utf-8') + b"\n")
            conn.close()
            print("Client disconnected")
        except socket.timeout:
            print("Socket server timed out waiting for a connection")
        except KeyboardInterrupt:
            print("Shutting down socket server...")
            running = False
        except Exception as e:
            print(f"Socket server error: {e}")

    server.close()
    print("Socket server stopped")

def main():
    global running
    try:
        emo_model = torch.load("acc_96.8", map_location=device)
    except Exception as e:
        print(f"Error loading model: {e}")
        return

    detector = Detector(face_model="retinaface", landmark_model="pfld")
    cap = cv.VideoCapture(0)
    if not cap.isOpened():
        print("Cannot open camera")
        return

    # Start the threads
    camera_thread_obj = threading.Thread(target=camera_thread, args=(cap, detector, emo_model))
    server_thread_obj = threading.Thread(target=socket_server_thread)

    camera_thread_obj.start()
    server_thread_obj.start()

    camera_thread_obj.join()
    server_thread_obj.join()
    cap.release()

if __name__ == "__main__":
    main()
