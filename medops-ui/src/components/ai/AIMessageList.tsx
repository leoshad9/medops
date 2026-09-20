import { Bot, Calendar, CreditCard, FlaskConical, HelpCircle, Pill } from "lucide-react";
import ReactMarkdown, { type Components } from "react-markdown";
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

const markdownComponents: Components = {
  p: ({ node: _node, ...props }) => (
    <p {...props} className="mb-1 last:mb-0 whitespace-pre-wrap leading-relaxed" />
  ),
  ul: ({ node: _node, ...props }) => (
    <ul {...props} className="mb-1 pl-5 last:mb-0" />
  ),
  ol: ({ node: _node, ...props }) => (
    <ol {...props} className="mb-1 pl-5 last:mb-0" />
  ),
  li: ({ node: _node, ...props }) => (
    <li {...props} className="mb-0.5 whitespace-pre-wrap leading-relaxed" />
  ),
  strong: ({ node: _node, ...props }) => (
    <strong {...props} className="font-semibold" />
  ),
  em: ({ node: _node, ...props }) => (
    <em {...props} className="italic" />
  ),
  code: ({ node: _node, ...props }) => (
    <code {...props} className="rounded bg-black/5 px-1 py-0.5 font-mono text-xs" />
  ),
};

/** Renders the conversation and shows quick actions until a patient message is present. */
export function AIMessageList({ messages, onQuickAction }: Readonly<AIMessageListProps>) {
  const showQuickActions = messages.length <= 1 && !messages.some((m) => m.role === "user");

  return (
    <div className="flex-1 space-y-3 overflow-y-auto px-3 py-3">
      {messages.map((message) => (
        <div
          key={message.id}
          className={`max-w-[80%] ${message.role === "user" ? "ml-auto" : ""}`}
        >
          <div
            className={`rounded-2xl px-4 py-2.5 text-sm ${
              message.role === "assistant"
                ? "rounded-tl-none bg-brand-primary-tint text-brand-ink"
                : "rounded-tr-none bg-brand-primary text-white"
            }`}
          >
            {message.role === "assistant" && messages.indexOf(message) === 0 ? (
              <Bot className="mb-1 h-4 w-4 text-brand-primary" />
            ) : null}
            <ReactMarkdown components={markdownComponents}>{message.content}</ReactMarkdown>
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
