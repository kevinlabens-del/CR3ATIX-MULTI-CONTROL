"""Visible, local-only TLS receiver. First use: verify fingerprint and enter one-time PIN."""
import datetime
import hashlib
import json
import os
import queue
import secrets
import socket
import ssl
import threading
import tkinter as tk
from pathlib import Path

import pyautogui
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID

PORT = 45721
HOME = Path(os.getenv("LOCALAPPDATA", Path.home())) / "CR3ATIX-MultiControl"
HOME.mkdir(parents=True, exist_ok=True)
CERT, KEY = HOME / "receiver.crt", HOME / "receiver.key"


def certificate():
    if not CERT.exists() or not KEY.exists():
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "CR3ATIX receiver")])
        now = datetime.datetime.now(datetime.timezone.utc)
        cert = (x509.CertificateBuilder().subject_name(subject).issuer_name(subject)
                .public_key(key.public_key()).serial_number(x509.random_serial_number())
                .not_valid_before(now - datetime.timedelta(days=1))
                .not_valid_after(now + datetime.timedelta(days=3650))
                .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
                .sign(key, hashes.SHA256()))
        KEY.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                          serialization.NoEncryption()))
        CERT.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
        try:
            os.chmod(KEY, 0o600)
        except OSError:
            pass
    der = ssl.PEM_cert_to_DER_cert(CERT.read_text())
    return hashlib.sha256(der).hexdigest().upper()


class Receiver:
    def __init__(self):
        self.root = tk.Tk()
        self.root.title("CR3@TIX MULTI CONTROL · Receiver")
        self.root.geometry("630x360")
        self.root.configure(bg="#101a2a")
        self.events = queue.Queue()
        self.running = True
        self.clients = set()
        self.lock = threading.Lock()
        self.pin = f"{secrets.randbelow(1000000):06d}"
        self.fingerprint = certificate()
        self.label("Receiver Windows · contrôle visible", 18)
        self.label("Adresse locale : port 45721 (indiquer l'IP Wi-Fi du PC sur Android)")
        self.label("Code temporaire : " + self.pin, 16)
        self.label("Empreinte SHA-256 du certificat TLS :")
        self.label(self.fingerprint[:32] + "\n" + self.fingerprint[32:], 10)
        self.status = self.label("En attente de connexion…")
        tk.Button(self.root, text="TOUT DÉCONNECTER / nouveau code", command=self.disconnect,
                  bg="#c64155", fg="white").pack(pady=10)
        self.root.protocol("WM_DELETE_WINDOW", self.close)
        threading.Thread(target=self.serve, daemon=True).start()
        self.root.after(100, self.pump)

    def label(self, text, size=12):
        widget = tk.Label(self.root, text=text, bg="#101a2a", fg="#ddf5ff",
                          font=("Segoe UI", size), wraplength=600)
        widget.pack(pady=6)
        return widget

    def disconnect(self):
        self.pin = f"{secrets.randbelow(1000000):06d}"
        with self.lock:
            for client in list(self.clients):
                try:
                    client.shutdown(socket.SHUT_RDWR)
                    client.close()
                except OSError:
                    pass
            self.clients.clear()
        for child in self.root.winfo_children():
            if isinstance(child, tk.Label) and child.cget("text").startswith("Code temporaire"):
                child.config(text="Code temporaire : " + self.pin)
        self.status.config(text="Connexions interrompues")

    def serve(self):
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(str(CERT), str(KEY))
        with socket.socket() as server:
            server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            server.bind(("0.0.0.0", PORT))
            server.listen(9)
            server.settimeout(1)
            while self.running:
                try:
                    client, _ = server.accept()
                    threading.Thread(target=self.handle, args=(context, client), daemon=True).start()
                except socket.timeout:
                    pass
                except OSError as exc:
                    self.events.put(("status", str(exc)))
                    break

    def handle(self, context, raw):
        try:
            with context.wrap_socket(raw, server_side=True) as client:
                client.settimeout(30)
                with self.lock:
                    self.clients.add(client)
                stream = client.makefile("r", encoding="utf-8", newline="\n")
                authenticated = False
                while self.running:
                    line = stream.readline(8193)
                    if not line or len(line) > 8192:
                        break
                    try:
                        message = json.loads(line)
                        if not isinstance(message, dict):
                            break
                    except (ValueError, TypeError):
                        break
                    if not authenticated:
                        if message.get("type") != "pair" or not secrets.compare_digest(str(message.get("pin", "")), self.pin):
                            client.sendall(b'{"ok":false}\n')
                            break
                        authenticated = True
                        client.sendall(b'{"ok":true}\n')
                        self.events.put(("status", "Android connecté · contrôle actif"))
                    else:
                        self.events.put(("input", message))
        except (OSError, ssl.SSLError, ValueError):
            pass
        finally:
            with self.lock:
                self.clients.discard(raw)
                self.clients = {c for c in self.clients if c.fileno() != -1}
            self.events.put(("status", "Appareil déconnecté"))

    def pump(self):
        for _ in range(100):
            try:
                kind, data = self.events.get_nowait()
            except queue.Empty:
                break
            if kind == "status":
                self.status.config(text=data)
            else:
                try:
                    self.input(data)
                except (ValueError, TypeError, KeyError, pyautogui.FailSafeException):
                    pass
        if self.running:
            self.root.after(20, self.pump)

    def input(self, msg):
        typ = msg.get("type")
        if typ == "move":
            pyautogui.moveRel(max(-200, min(200, int(msg.get("dx", 0)))),
                              max(-200, min(200, int(msg.get("dy", 0)))), duration=0)
        elif typ == "click" and msg.get("button") in ("left", "right", "middle"):
            pyautogui.click(button=msg["button"])
        elif typ == "scroll":
            pyautogui.scroll(max(-8, min(8, int(msg.get("steps", 0)))))
        elif typ == "key" and msg.get("key") in ("enter", "esc", "tab", "backspace", "delete", "up", "down", "left", "right", "space", "home", "end", "pageup", "pagedown"):
            pyautogui.press(msg["key"])
        elif typ == "text":
            text = str(msg.get("text", ""))[:2048]
            self.root.clipboard_clear()
            self.root.clipboard_append(text)
            self.root.update()
            pyautogui.hotkey("ctrl", "v")
        elif typ == "media" and msg.get("key") in ("playpause", "nexttrack", "prevtrack", "volumeup", "volumedown", "volumemute"):
            pyautogui.press(msg["key"])
        elif typ == "ping":
            pass

    def close(self):
        self.running = False
        self.disconnect()
        self.root.destroy()


if __name__ == "__main__":
    Receiver().root.mainloop()
