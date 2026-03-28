from flask import Flask, request, jsonify
from openai import OpenAI
from dotenv import load_dotenv
import os
import json
import requests

load_dotenv()
app = Flask(__name__)
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))

SUBSCRIBERS_FILE = "subscribers.json"

def load_subscribers():
    try:
        with open(SUBSCRIBERS_FILE, "r") as f:
            return json.load(f)
    except:
        return []

def save_subscribers(subscribers):
    with open(SUBSCRIBERS_FILE, "w") as f:
        json.dump(subscribers, f)

# ── Q&A Analysis ──────────────────────────────────────────────────────────────

@app.route("/analyze", methods=["POST"])
def analyze():
    email_content = request.json.get("email")

    response = client.chat.completions.create(
        model="gpt-5.4",
        messages=[
            {"role": "system", "content": """You are an extremely formal and 
            overly thorough email assistant. Write a response to the email that is 
            5x more formal and lengthy than necessary. Use overly complex vocabulary,
            unnecessary disclaimers, and excessive formality. 
            Return ONLY the response text. No JSON, no labels, no formatting.
            Always use "Hello," to start and "Best regards," to end, regardless of the email content."""},
            {"role": "user", "content": email_content}
        ]
    )

    return response.choices[0].message.content

# ── Subscribers ───────────────────────────────────────────────────────────────

@app.route("/subscribers")
def get_subscribers():
    return jsonify({"subscribers": load_subscribers()})

@app.route("/subscribe", methods=["POST"])
def subscribe():
    email = request.json.get("email")
    subscribers = load_subscribers()
    if email not in subscribers:
        subscribers.append(email)
        save_subscribers(subscribers)
        return jsonify({"result": f"Added {email}!"})
    return jsonify({"result": "Already subscribed."})

@app.route("/unsubscribe", methods=["POST"])
def unsubscribe():
    email = request.json.get("email")
    subscribers = load_subscribers()
    if email in subscribers:
        subscribers.remove(email)
        save_subscribers(subscribers)
        return jsonify({"result": f"Removed {email}."})
    return jsonify({"result": "Email not found."})

# ── Newsletter ─────────────────────────────────────────────────────────────────

@app.route("/send-newsletter", methods=["POST"])
def send_newsletter():
    subject = request.json.get("subject")
    message = request.json.get("message")
    subscribers = load_subscribers()

    if not subscribers:
        return jsonify({"result": "No subscribers to send to!"})

    # Tell Java to send the emails
    response = requests.post("http://localhost:8080/send-newsletter", json={
        "subject": subject,
        "message": message,
        "subscribers": subscribers
    })

    return jsonify({"result": f"Sent to {len(subscribers)} subscribers!"})

# ── Admin Page ─────────────────────────────────────────────────────────────────

@app.route("/admin")
def admin():
    return '''
    <html>
    <head>
        <title>OverkillEmail Admin</title>
        <style>
            body { font-family: Arial; max-width: 650px; margin: 50px auto; padding: 20px; }
            h1 { border-bottom: 2px solid black; padding-bottom: 10px; }
            h3 { margin-top: 30px; }
            input, textarea { width: 100%; padding: 10px; margin: 8px 0;
                              font-size: 15px; box-sizing: border-box; }
            button { background: black; color: white; padding: 10px 20px;
                     font-size: 15px; cursor: pointer; border: none; margin-top: 5px; }
            button:hover { background: #333; }
            #status { color: green; margin-top: 10px; font-weight: bold; }
            .subscriber { padding: 5px 0; border-bottom: 1px solid #eee; }
        </style>
    </head>
    <body>
        <h1>OverkillEmail Admin Panel</h1>

        <h3>Send Newsletter</h3>
        <input type="text" id="subject" placeholder="Subject line" />
        <textarea id="message" rows="10" placeholder="Type your newsletter here..."></textarea>
        <button onclick="sendNewsletter()">Send to All Subscribers</button>
        <p id="status"></p>

        <h3>Add Subscriber</h3>
        <input type="text" id="newEmail" placeholder="email@example.com" />
        <button onclick="addSubscriber()">Add Subscriber</button>

        <h3>Remove Subscriber</h3>
        <input type="text" id="removeEmail" placeholder="email@example.com" />
        <button onclick="removeSubscriber()">Remove Subscriber</button>

        <h3>Current Subscribers</h3>
        <div id="subscriberList">Loading...</div>

        <script>
            function loadSubscribers() {
                fetch("/subscribers")
                    .then(r => r.json())
                    .then(data => {
                        const list = document.getElementById("subscriberList");
                        if (data.subscribers.length === 0) {
                            list.innerHTML = "<p>No subscribers yet.</p>";
                        } else {
                            list.innerHTML = data.subscribers
                                .map(e => "<div class='subscriber'>" + e + "</div>")
                                .join("");
                        }
                    });
            }

            function sendNewsletter() {
                const subject = document.getElementById("subject").value;
                const message = document.getElementById("message").value;
                if (!subject || !message) {
                    document.getElementById("status").innerText = "Please fill in both fields!";
                    return;
                }
                document.getElementById("status").innerText = "Sending...";
                fetch("/send-newsletter", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ subject, message })
                })
                .then(r => r.json())
                .then(data => {
                    document.getElementById("status").innerText = data.result;
                });
            }

            function addSubscriber() {
                const email = document.getElementById("newEmail").value;
                if (!email) return;
                fetch("/subscribe", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ email })
                })
                .then(r => r.json())
                .then(data => {
                    document.getElementById("status").innerText = data.result;
                    document.getElementById("newEmail").value = "";
                    loadSubscribers();
                });
            }

            function removeSubscriber() {
                const email = document.getElementById("removeEmail").value;
                if (!email) return;
                fetch("/unsubscribe", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ email })
                })
                .then(r => r.json())
                .then(data => {
                    document.getElementById("status").innerText = data.result;
                    document.getElementById("removeEmail").value = "";
                    loadSubscribers();
                });
            }

            loadSubscribers();
        </script>
    </body>
    </html>
    '''

if __name__ == "__main__":
    print("Python server running on port 5000...")
    app.run(port=5000)
