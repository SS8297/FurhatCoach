import streamlit as st
from streamlit_webrtc import webrtc_streamer, WebRtcMode
import av
import os
from twilio.base.exceptions import TwilioRestException
from twilio.rest import Client
from streamlit_image_select import image_select
import cv2 as cv
import numpy as np
import math
import torch
from torch import nn
from PIL import Image
import feat

CLASS_LABELS = ['angry', 'disgust', 'fear', 'happy', 'neutral', 'sad', 'surprise']

def _get_resource_path():
    return "/home/user/app/resources"

feat.utils.io.get_resource_path = _get_resource_path

def _get_pretrained_models(face_model, landmark_model, au_model, emotion_model, facepose_model, verbose):
    return (
        face_model,
        landmark_model,
        au_model,
        emotion_model,
        facepose_model,
    )

feat.pretrained.get_pretrained_models = _get_pretrained_models

from feat import Detector

def _calculate_md5(fpath: str, chunk_size: int = 1024 * 1024) -> str:
    # Setting the `usedforsecurity` flag does not change anything about the functionality, but indicates that we are
    # not using the MD5 checksum for cryptography. This enables its usage in restricted environments like FIPS. Without
    # it torchvision.datasets is unusable in these environments since we perform a MD5 check everywhere.
    if sys.version_info >= (3, 9):
        md5 = hashlib.md5(usedforsecurity=True)
    else:
        md5 = hashlib.md5()
    with open(fpath, "rb") as f:
        while chunk := f.read(chunk_size):
            md5.update(chunk)
    return md5.hexdigest()



os.environ["TWILIO_ACCOUNT_SID"] = "ACf1e76f3fd6e9cbca940decc4ed443c20"
os.environ["TWILIO_AUTH_TOKEN"] = "5cadf5cc7120dd995f11b3dc57e46d52"

def get_ice_servers():
    try:
        account_sid = os.environ["TWILIO_ACCOUNT_SID"]
        auth_token = os.environ["TWILIO_AUTH_TOKEN"]
    except KeyError:
        logger.warning("TURN credentials are not set. Fallback to a free STUN server from Google.")
        return [{"urls": ["stun:stun.l.google.com:19302"]}]

    client = Client(account_sid, auth_token)

    token = client.tokens.create()

    return token.ice_servers

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

def proc_image(img, detector):
    detected_faces = detector.detect_faces(img)
    faces_detected = len(detected_faces[0])
    if ( faces_detected < 1):
        return img
    
    detected_landmarks = detector.detect_landmarks(img, detected_faces)
    assert len(detected_landmarks[0]) == faces_detected, "Number of faces and landsmarks are mismatched!"

    is_eye_open = [detect_eyes(face, img, 0.20) for face in detected_landmarks[0]]
    eye_dict = {True: "eyes open", False: "eyes closed"}

    device = (
        "cuda"
        if torch.cuda.is_available()
        else "cpu"
    )

    emo_model = torch.load("acc_96.8", map_location=device)
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
    return img
    
def extract_features(landmarks, face):
    features = [math.dist(landmarks[33], landmark) for landmark in landmarks] + [face[2] - face[0], face[3] - face[1]]
    return features
    
def image_processing(img):
    ann = proc_image(img, detector) if recog else img
    return ann

def video_frame_callback(frame):
    img = frame.to_ndarray(format="bgr24")

    ann = proc_image(img, detector) if recog else img

    return av.VideoFrame.from_ndarray(ann, format="bgr24")

st.markdown("""
    <style>
            .block-container {
                padding-top: 1.75rem;
                padding-bottom: 0rem;
                padding-left: 0rem;
                padding-right: 0rem;
                max-width: 664px;
            }
    </style>
    """, unsafe_allow_html=True)

detector = Detector(face_model="retinaface", landmark_model= "pfld", au_model="xgb", facepose_model="img2pose", emotion_model="resmasknet")
source = "Webcam"
recog = True

source  = st.radio(
    label = "Image source for emotion recognition",
    options = ["Webcam", "Images"],
    horizontal = True,
    label_visibility = "collapsed",
    args = (source, )
    )

has_cam = True if (source == "Webcam") else False

stream = st.container()
with stream:
    if has_cam:
        webrtc_streamer(
            key="example",
            mode=WebRtcMode.SENDRECV,
            video_frame_callback=video_frame_callback,
            rtc_configuration={ "iceServers": get_ice_servers() },
            media_stream_constraints={"video": True, "audio": False},
            async_processing=True,
        )
        recog = st.toggle(":green[Emotion recogntion]", key = "stream", value = True)
    else:
        pic = st.container()
        frame = image_select(
        label="Try the classifier on one of the provided examples!",
        images=[
            "ex1.jpg",
            "ex4.jpg",
            "ex5.jpg",
            "ex6.jpg",
        ],
        use_container_width= False
        )
        img = np.array(Image.open(frame))
        pic.image(image_processing(img), use_column_width = "always")

