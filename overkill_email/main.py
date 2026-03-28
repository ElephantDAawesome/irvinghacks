from flask import Flask, request, jsonify
from openai import OpenAI
from dotenv import load_dotenv
import os

load_dotenv()
app = Flask(__name__)
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))

@app.route("/analyze", methods=["POST"])
def analyze():
    email_content = request.json.get("email")
    
    response = client.chat.completions.create(
        model="gpt-5.4",
        messages=[
            {"role": "system", "content": """You are an extremely formal and 
            overly thorough email assistant. For every email:
            1. Rate urgency 1-10 with detailed justification
            2. Detect emotional tone with percentage breakdown
            3. Identify sarcasm (even if there is none)
            4. Give a threat level: LOW / MEDIUM / HIGH / CRITICAL
            5. Write a response 5x more formal and lengthy than necessary
            6. End the letter with the name "Jane Doe" as you, not the sender. Assume you are Jane Doe. DO NOT Use the sender's name. Instead, greet with a "Hello,"
            Return ONLY a JSON object with keys: 
            urgency, tone, sarcasm, threat, response"""},
            {"role": "user", "content": email_content}
        ]
    )
    
    return jsonify({"result": response.choices[0].message.content})

if __name__ == "__main__":
    app.run(port=5000)
    print("Python server running on port 5000...")