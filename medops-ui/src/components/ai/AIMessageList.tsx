import { Bot, Calendar, CreditCard, FlaskConical, HelpCircle, Pill } from "lucide-react";
import ReactMarkdown, { type Components } from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeRaw from "rehype-raw";
import rehypeSanitize from "rehype-sanitize";
import type { ComponentType } from "react";

import type { AIMessage, AISuggestion } from "../../services/aiAssistantService";

interface AIMessageListProps {
  messages: AIMessage[];
  onQuickAction: (actionId: string, query: string) => void;
  isPending?: boolean;
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
  del: ({ node: _node, ...props }) => (
    <del {...props} className="line-through" />
  ),
  code: ({ node: _node, ...props }) => (
    <code {...props} className="rounded bg-black/5 px-1 py-0.5 font-mono text-xs" />
  ),
  pre: ({ node: _node, ...props }) => (
    <pre {...props} className="overflow-x-auto rounded-lg bg-black/5 p-3 text-xs" />
  ),
  blockquote: ({ node: _node, ...props }) => (
    <blockquote {...props} className="border-l-2 border-brand-primary pl-3 italic" />
  ),
};

const parseJsonBlock = <T,>(content: string): T | null => {
  const jsonBlock = content.match(/```json\s*([\s\S]*?)\s*```/i);
  if (!jsonBlock) return null;

  try {
    return JSON.parse(jsonBlock[1]) as T;
  } catch {
    return null;
  }
};

const parseActionsFromMessage = (content: string) => {
  const parsed = parseJsonBlock<{ actions?: Array<{ id?: string; label?: string; query?: string }> }>(content);
  return Array.isArray(parsed?.actions) ? parsed.actions : [];
};

const resolveActionQuery = (action: { id?: string; label?: string; query?: string }, onQuickAction: AIMessageListProps["onQuickAction"]) => {
  const actionId = action.id ?? action.label ?? "";

  try {
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    const resolver = (window as any).__medops_action_resolver as Record<string, { resolved?: unknown }> | undefined;
    if (actionId) {
      const resolved = resolver?.[actionId]?.resolved;
      if (resolved && typeof resolved === "object" && "query" in resolved) {
        const query = typeof resolved.query === "string" ? resolved.query : JSON.stringify(resolved.query);
        onQuickAction(actionId, query);
        return;
      }
    }
  } catch {
    // fall through to default
  }

  onQuickAction(actionId, action.query ?? action.label ?? "");
};

const renderSuggestions = (suggestions: AISuggestion[], onQuickAction: AIMessageListProps["onQuickAction"], isPending: boolean) => {
  if (!suggestions.length) return null;

  return (
    <div className="mt-2 flex flex-wrap gap-2">
      {suggestions.map((suggestion, idx) => {
        const label = typeof suggestion === "string" ? suggestion : suggestion.label ?? suggestion.query ?? "";
        const query = typeof suggestion === "string" ? suggestion : suggestion.query ?? label;
        if (!label) return null;
        return (
          <button
            key={idx}
            type="button"
            onClick={() => onQuickAction(`suggestion_${idx}`, query)}
            disabled={isPending}
            className="rounded-full border border-brand-line bg-white px-3 py-1.5 text-xs font-medium text-brand-ink hover:border-brand-primary hover:bg-brand-paper disabled:cursor-not-allowed disabled:opacity-50"
          >
            {label}
          </button>
        );
      })}
    </div>
  );
};

/**
 * Renders the conversation and keeps the topic chips reachable under every turn: they
 * are the fastest way to jump to another subject mid-conversation, and a footer that
 * emptied itself after the first question left no way to switch topics at all.
 */
export function AIMessageList({ messages, onQuickAction, isPending = false }: Readonly<AIMessageListProps>) {
  const hasUserMessage = messages.some((m) => m.role === "user");
  const showTopicHeading = messages.length <= 1 && !hasUserMessage;

  return (
    <div className="flex-1 space-y-3 overflow-y-auto px-3 py-3">
      {messages.map((message, index) => {
        const actions = message.role === "assistant" ? parseActionsFromMessage(message.content) : [];
        const isFirstAssistantMessage = message.role === "assistant" && index === 0;

        return (
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
              {isFirstAssistantMessage ? (
                <Bot className="mb-1 h-4 w-4 text-brand-primary" />
              ) : null}
              <ReactMarkdown
                components={markdownComponents}
                remarkPlugins={[remarkGfm]}
                rehypePlugins={[rehypeRaw, rehypeSanitize]}
              >
                {message.content}
              </ReactMarkdown>

              {message.role === "assistant" && actions.length > 0 ? (
                <div className="mt-2 flex flex-wrap gap-2">
                  {actions.map((act) => (
                    <button
                      key={act.id}
                      type="button"
                      onClick={() => resolveActionQuery(act, onQuickAction)}
                      disabled={isPending}
                      className="rounded-md bg-brand-primary px-3 py-1 text-xs font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {act.label}
                    </button>
                  ))}
                </div>
              ) : null}

              {message.role === "assistant" && message.suggestions?.length
                ? renderSuggestions(message.suggestions, onQuickAction, isPending)
                : null}
            </div>
          </div>
        );
      })}

      <div className="mt-4">
        {showTopicHeading && <p className="text-xs font-semibold text-brand-muted mb-2">What can I help you with?</p>}
        <div className="flex flex-wrap gap-2">
          {QUICK_ACTIONS.map((action) => {
            const Icon = action.icon;
            return (
              <button
                key={action.id}
                type="button"
                onClick={() => onQuickAction(action.id, action.query)}
                disabled={isPending}
                className="flex items-center gap-1.5 rounded-full border border-brand-line bg-white px-3 py-1.5 text-xs font-medium text-brand-ink transition hover:border-brand-primary hover:bg-brand-paper cursor-pointer disabled:cursor-not-allowed disabled:opacity-50"
              >
                <Icon className="h-3 w-3 text-brand-primary" />
                {action.label}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
