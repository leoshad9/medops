import { Bot, Calendar, CreditCard, FlaskConical, HelpCircle, Pill } from "lucide-react";
import type { ComponentType } from "react";

import type { AIMessage } from "../../services/aiAssistantService";

interface AIMessageListProps {
  messages: AIMessage[];
  onQuickAction: (actionId: string, query: string) => void;
}

interface QuickActionItem {
  id: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
  query: string;
}

const QUICK_ACTIONS: QuickActionItem[] = [
  { id: "appointments", label: "Appointments", icon: Calendar, query: "I need help with my upcoming appointments. Can you show me what I have scheduled?" },
  { id: "labs", label: "Reports & Labs", icon: FlaskConical, query: "I want to check my recent lab test results. Where can I find them?" },
  { id: "prescriptions", label: "Prescriptions", icon: Pill, query: "Can you tell me about my current prescriptions and when I need a refill?" },
  { id: "billing", label: "Billing", icon: CreditCard, query: "I have a question about my billing statement. How can I view or pay my invoice?" },
  { id: "help", label: "Using MedOps", icon: HelpCircle, query: "How do I use the MedOps patient portal? What features are available?" },
];

export function AIMessageList({ messages, onQuickAction }: Readonly<AIMessageListProps>) {
  const showQuickActions = messages.length <= 1 && !messages.some((m) => m.role === "user");

  return (
    <div className="flex-1 space-y-3 overflow-y-auto px-3 py-3">
      {messages.map((message) => (
        <div key={message.id} className="max-w-[80%]">
          <div
            className={`rounded-2xl px-4 py-2.5 text-sm ${
              message.role === "assistant"
                ? "rounded-tl-none bg-brand-primary-tint text-brand-ink"
                : "ml-auto rounded-tr-none bg-brand-primary text-white"
            }`}
          >
            {message.role === "assistant" && messages.indexOf(message) === 0 ? (
              <Bot className="mb-1 h-4 w-4 text-brand-primary" />
            ) : null}
            <p className="whitespace-pre-wrap leading-relaxed">{message.content}</p>
          </div>
        </div>
      ))}

      {showQuickActions && (
        <div className="mt-4">
          <p className="text-xs font-semibold text-brand-muted mb-2">What can I help you with?</p>
          <div className="flex flex-wrap gap-2">
            {QUICK_ACTIONS.map((action) => {
              const Icon = action.icon;
              return (
                <button
                  key={action.id}
                  type="button"
                  onClick={() => onQuickAction(action.id, action.query)}
                  className="flex items-center gap-1.5 rounded-full border border-brand-line bg-white px-3 py-1.5 text-xs font-medium text-brand-ink transition hover:border-brand-primary hover:bg-brand-paper cursor-pointer"
                >
                  <Icon className="h-3 w-3 text-brand-primary" />
                  {action.label}
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
