import type { ApiResponse } from "../types/api";

export interface AIMessage {
  id: string;
  role: "assistant" | "user";
  content: string;
  timestamp: string;
}

export interface AIChatResponse {
  message: AIMessage;
}

export interface AIQuickAction {
  id: string;
  label: string;
  icon: string;
  query: string;
}

export const AI_QUICK_ACTIONS: AIQuickAction[] = [
  { id: "appointments", label: "Appointments", icon: "calendar", query: "I need help with my upcoming appointments. Can you show me what I have scheduled?" },
  { id: "labs", label: "Reports & Labs", icon: "flask", query: "I want to check my recent lab test results. Where can I find them?" },
  { id: "prescriptions", label: "Prescriptions", icon: "pill", query: "Can you tell me about my current prescriptions and when I need a refill?" },
  { id: "billing", label: "Billing", icon: "credit-card", query: "I have a question about my billing statement. How can I view or pay my invoice?" },
  { id: "help", label: "Using MedOps", icon: "help-circle", query: "How do I use the MedOps patient portal? What features are available?" },
];

const MOCK_RESPONSES: Record<string, string> = {
  appointments:
    "You can view all your upcoming and past appointments under the **Appointments** section in the sidebar. To book a new appointment, click \"Book an Appointment\".\n\nYour next appointment is with Dr. Sarah Chen on Thursday, Sep 18 at 10:30 AM. Would you like me to send you a reminder?",
  labs:
    "Your recent lab results are available in the **Lab Reports** section under your dashboard. Click on any report to see the full details.\n\nYour most recent blood work (Sep 10) shows all values within normal ranges. Your cholesterol panel is next scheduled for Oct 5.",
  prescriptions:
    "You have 2 active prescriptions:\n\n1. **Lisinopril 10mg** — 1 refill remaining. Last filled Sep 5.\n2. **Metformin 500mg** — 0 refills remaining. Please contact your doctor for a renewal.\n\nYou can request a refill through the Prescriptions section.",
  billing:
    "Your most recent invoice ($247.50) is ready to view in the **Billing** section. Insurance covered $180, leaving a balance of $67.50.\n\nYou can pay online via the payment link in your billing portal, or use the mobile app. Need a copy sent to your email?",
  help:
    "Welcome to MedOps! Here's what you can do:\n\n- **Dashboard**: Quick overview of your health stats and recent activity\n- **Appointments**: View, book, or cancel appointments\n- **Prescriptions**: Check active prescriptions and request refills\n- **Lab Reports**: Access your results\n- **Medical Records**: View documents and visit history\n- **Billing**: Pay invoices and manage insurance\n- **Profile**: Update personal and medical information\n\nNeed more help? Our support team is available 24/7 at +91 11 4567 8900.",
  default:
    "I can help you with appointments, lab reports, prescriptions, billing, and using MedOps. Try clicking one of the quick actions below, or ask me anything!",
};

function mockAIResponse(query: string): string {
  const lower = query.toLowerCase();

  if (lower.includes("appointment")) return MOCK_RESPONSES.appointments;
  if (lower.includes("lab") || lower.includes("report") || lower.includes("test")) return MOCK_RESPONSES.labs;
  if (lower.includes("prescription") || lower.includes("refill") || lower.includes("medication"))
    return MOCK_RESPONSES.prescriptions;
  if (lower.includes("billing") || lower.includes("invoice") || lower.includes("payment") || lower.includes("charge"))
    return MOCK_RESPONSES.billing;
  if (lower.includes("help") || lower.includes("how to") || lower.includes("use")) return MOCK_RESPONSES.help;

  return MOCK_RESPONSES.default;
}

export async function getAIResponse(query: string): Promise<AIChatResponse> {
  return new Promise((resolve) => {
    setTimeout(() => {
      const response: AIChatResponse = {
        message: {
          id: crypto.randomUUID(),
          role: "assistant",
          content: mockAIResponse(query),
          timestamp: new Date().toISOString(),
        },
      };
      resolve(response);
    }, 800 + Math.random() * 400);
  });
}

// Future integration: swap mockAIResponse for a real API call:
// export async function getAIResponse(query: string): Promise<AIChatResponse> {
//   const response = await api.post<ApiResponse<AIChatResponse>>("/api/v1/assistant/chat", { query });
//   return response.data.data;
// }

export function useAIService() {
  return {
    getAIResponse,
    quickActions: AI_QUICK_ACTIONS,
  };
}

export type { ApiResponse };
