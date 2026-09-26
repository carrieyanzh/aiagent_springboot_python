import os
import json
import pika
from dotenv import load_dotenv
from groq import Groq

# 1. Load environment variables from .env file
load_dotenv()

# 2. Initialize the Groq client (It automatically picks up GROQ_API_KEY from .env)
client = Groq()
#MODEL_ID = 'llama3-8b-8192'  # Excellent, fast, and free-tier friendly model on Groq
MODEL_ID = 'qwen/qwen3.8-27b'


RABBITMQ_HOST = '192.168.1.122'  # 👈 Make sure this matches your Raspberry Pi 400 IP
INPUT_QUEUE = 'inventory.low_stock.queue'
OUTPUT_QUEUE = 'inventory.purchase_order.queue'

def call_groq_agent(system_instruction, user_prompt):
    """Universal AI role invocation using Groq"""
    response = client.chat.completions.create(
        model=MODEL_ID,
        messages=[
            {"role": "system", "content": system_instruction},
            {"role": "user", "content": user_prompt}
        ],
        temperature=0.2  # Low randomness for strict business rules
    )
    return response.choices[0].message.content

def process_workflow(stock_context):
    print(f"\n🤖 [AI Received Low-Stock Context]: {stock_context}")
    
    current_report = f"Initial stock request: Product {stock_context.get('name')}, current stock is only {stock_context.get('currentStock')}."
    feedback = ""
    
    # 4-turn multi-agent collaboration loop
    for turn in range(1, 5):
        print(f"🔄 --- Agent Turn {turn} ---")
        
        if turn % 2 != 0:
            # Odd turns: Inventory Analyst (Agent 1)
            prompt = f"Current report state: {current_report}\nAuditor feedback: {feedback}\nBased on this, draft a formal replenishment quantity request."
            system_rule = "You are a senior inventory analyst. Your job is to draft procurement requests. If the auditor rejects due to budget, reduce the quantity to mitigate financial risk."
            current_report = call_groq_agent(system_rule, prompt)
            print(f"📝 [Agent 1 Analyst] Output:\n{current_report}\n")
        else:
            # Even turns: Purchase Auditor (Agent 2)
            prompt = f"Review this replenishment report submitted by the analyst:\n{current_report}\nCheck if the quantity is too aggressive. If it is unreasonable, give specific modification feedback. If you completely agree, your reply must include and ONLY include the keyword: [APPROVED]"
            system_rule = "You are a strict financial and procurement manager. For high-value items or orders exceeding 10 units, you must reject it to protect cash flow unless they lower the quantity. Do not approve until it is safe."
            feedback = call_groq_agent(system_rule, prompt)
            print(f"🧐 [Agent 2 Auditor] Feedback:\n{feedback}\n")
            
            if "[APPROVED]" in feedback:
                print("✅ [Workflow Aligned] The Purchase Auditor has approved the report!")
                return current_report
                
    print("⚠️ [Workflow Maxed Out] Reached 4 turns maximum. Returning final compromise version.")
    return current_report

def on_message_callback(ch, method, properties, body):
    try:
        stock_context = json.loads(body.decode('utf-8'))
        final_po = process_workflow(stock_context)
        
        # Send final PO back to Raspberry Pi RabbitMQ for Spring Boot to consume
        ch.basic_publish(
            exchange='',
            routing_key=OUTPUT_QUEUE,
            body=json.dumps({"status": "AI_APPROVED", "purchaseOrderDetails": final_po}, ensure_ascii=False)
        )
        print("📤 [AI Worker] Sent final approved purchase order back to the queue.")
    except Exception as e:
        print(f"Processing failed: {e}")
    finally:
        ch.basic_ack(delivery_tag=method.delivery_tag)

# 3. Connect and listen to Raspberry Pi 400 RabbitMQ
connection = pika.BlockingConnection(pika.ConnectionParameters(host=RABBITMQ_HOST))
channel = connection.channel()
channel.queue_declare(queue=INPUT_QUEUE, durable=True)
channel.basic_qos(prefetch_count=1)
channel.basic_consume(queue=INPUT_QUEUE, on_message_callback=on_message_callback)

print("🚀 [Groq AI Agent Worker] Actively listening to low-stock events on Raspberry Pi...")
channel.start_consuming()
