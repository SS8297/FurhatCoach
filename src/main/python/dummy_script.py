import socket
import json
import time
# Import OpenCV, PyFeat, and other necessary libraries here

# Set up a socket server to communicate with the Kotlin application
HOST, PORT = 'localhost', 9999
server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server.bind((HOST, PORT))
server.listen(1)

# Emotion cache and timestamp
last_emotion_index = -1
last_time = 0

emotions = ["angry", "disgust", "fear", "happy", "sad", "surprise", "neutral", "eyes_opened", "eyes_closed"]
# Emotion detection function (to be implemented)
def detect_emotion():
    global last_emotion_index, last_time, emotions
    current_time = time.time()

    # Check if the last emotion was detected less than 10 seconds ago
    if current_time - last_time < 5:
        return emotions[last_emotion_index]

    # Move to the next emotion in the list, cycling back to the start if necessary
    last_emotion_index = (last_emotion_index + 1) % len(emotions)
    emotion = emotions[last_emotion_index]

    last_time = current_time
    return emotion
    ##return emotion
    return "sad"


while True:
    try:
        conn, addr = server.accept()
        try:
            while True:
                data = conn.recv(1024)
                print("Received data:", data.decode())
                if not data:
                    break

                emotion = detect_emotion()
                response = json.dumps({'patientState': emotion})
                print("Sending response:", response)
                conn.sendall(response.encode('utf-8') + b"\n")
                conn.flush()
        finally:
            conn.close()
    except Exception as e:
        print(f"Error: {e}")