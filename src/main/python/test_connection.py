import socket

## NOTE: Run this to test the connection with the script.py

HOST, PORT = 'localhost', 9999
client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
client_socket.connect((HOST, PORT))

client_socket.sendall("Request emotion".encode())
response = client_socket.recv(1024).decode()
print("Received:", response)

client_socket.close()
