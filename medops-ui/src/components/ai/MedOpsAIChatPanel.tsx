import axios from "axios";
import { Bot, X } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { AIChatInput } from "./AIChatInput";
import { AIMessageList } from "./AIMessageList";
import { ActionDispatcher } from "./ActionDispatcher";
import type { AIMessage } from "../../services/aiAssistantService";
import type { ErrorResponse } from "../../types/api";
import { getAIResponse } from "../../services/aiAssistantService";

interface MedOpsAIChatPanelProps {
  isOpen: boolean;
  onClose: () => void;
  firstName?: string;
}

/** Renders the assistant when open and retains its conversation while mounted. */
export function MedOpsAIChatPanel({ isOpen, onClose, firstName = "there" }: Readonly<MedOpsAIChatPanelProps>) {
  const [messages, setMessages] = useState<AIMessage[]>([]);
  const [inputValue, setInputValue] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  // appointment times render in the caller's local zone instead of UTC.
  const timeZone = useMemo<string>(() => Intl.DateTimeFormat().resolvedOptions().timeZone, []);

  // Load persisted conversation from localStorage on mount
  useEffect(() => {
    try {
      const raw = localStorage.getItem("medops_ai_conv");
      if (raw) {
        const parsed = JSON.parse(raw) as AIMessage[];
        if (Array.isArray(parsed) && parsed.length > 0) {
          setMessages(parsed);
        }
      }
    } catch {
      // ignore parse errors
    }
  }, []);

  // Persist conversation whenever messages change
  useEffect(() => {
    try {
      localStorage.setItem("medops_ai_conv", JSON.stringify(messages));
    } catch {
      // ignore storage errors
    }
  }, [messages]);

  if (!isOpen) return null;

  /** Sends the query associated with a selected quick action. */
  const handleQuickAction = (actionId: string, query: string) => {
    if (actionId === "back") {
      // Reset conversation to show quick actions and greeting again
      setMessages([]);
      setInputValue("");
      return;
    }
    handleSendQuery(query);
  };

  const handleResetConversation = () => {
    setMessages([]);
    setInputValue("");
    try {
      localStorage.removeItem("medops_ai_conv");
    } catch {
      // ignore
    }
  };

  const applyActionResolver = (response: { raw?: { actions?: Array<{ id?: string | null; resolved?: unknown; label?: string | null } | null> | null } | null }) => {
    try {
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-ignore
      (window as any).__medops_action_resolver = {};
      const dto = response?.raw;
      if (!dto || !Array.isArray(dto.actions)) return;

      for (const action of dto.actions) {
        if (!action || !action.id) continue;
        try {
          // eslint-disable-next-line @typescript-eslint/ban-ts-comment
          // @ts-ignore
          (window as any).__medops_action_resolver[action.id] = {
            resolved: action.resolved ?? null,
            label: action.label ?? null,
          };
        } catch {
          // ignore
        }
      }
    } catch {
      // ignore
    }
  };

  const createErrorMessage = (error: unknown): AIMessage => {
    const apiMessage = axios.isAxiosError(error)
      ? (error.response?.data as ErrorResponse | undefined)?.error?.message
      : undefined;

    return {
      id: crypto.randomUUID(),
      role: "assistant",
      content: apiMessage || "Sorry, I'm having trouble connecting. Please try again later.",
      timestamp: new Date().toISOString(),
    };
  };

  /** Adds a nonblank query, followed by an assistant response or connection-error message. */
  const handleSendQuery = async (query: string) => {
    if (!query.trim() || isLoading) return;

    const userMessage: AIMessage = {
      id: crypto.randomUUID(),
      role: "user",
      content: query,
      timestamp: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInputValue("");
    setIsLoading(true);

    try {
      const response = await getAIResponse(query, timeZone);
      applyActionResolver(response);
      setMessages((prev) => [...prev, response.message]);
    } catch (error) {
      setMessages((prev) => [...prev, createErrorMessage(error)]);
    } finally {
      setIsLoading(false);
    }
  };

  /** Sends the current input value. */
  const handleSend = () => {
    handleSendQuery(inputValue);
  };

  /** Closes the panel unless a response is currently loading. */
  const handleClose = () => {
    if (isLoading) return;
    onClose();
  };

  // Show greeting + quick action chips only on first open (empty conversation)
  const showQuickActions = messages.length === 0;
  // Prepend a greeting message on first open; subsequent messages come from the conversation
  const initialMessage: AIMessage | null = showQuickActions
    ? {
        id: crypto.randomUUID(),
        role: "assistant",
        content: `Hi ${firstName} 👋\nHow can I help you today?`,
        timestamp: new Date().toISOString(),
      }
    : null;

  const displayMessages = initialMessage ? [initialMessage, ...messages] : messages;

  return (
    <div className="fixed inset-y-0 right-0 z-50 flex h-dvh w-full sm:max-w-sm md:max-w-md lg:max-w-lg xl:max-w-md flex-col overflow-hidden border-l border-brand-line bg-white shadow-xl animate-in slide-in-from-right safe-top safe-bottom">
      <div className="flex items-center justify-between border-b border-brand-line p-3 sm:p-4">
        <div className="flex items-center gap-2">
          <Bot className="h-5 w-5 text-brand-primary" />
          <h2 className="fluid-text-sm font-bold text-brand-ink truncate">MedOps AI Assistant</h2>
        </div>
        <button
          type="button"
          onClick={handleClose}
          disabled={isLoading}
          className="flex h-8 w-8 items-center justify-center rounded-lg text-brand-muted hover:bg-brand-paper hover:text-brand-ink disabled:cursor-not-allowed touch-target"
          aria-label="Close chat"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      <div className="p-3 sm:p-4">
        <button
          type="button"
          onClick={handleResetConversation}
          className="text-xs text-brand-muted hover:text-brand-ink"
        >
          Reset conversation
        </button>
      </div>

      <AIMessageList messages={displayMessages} onQuickAction={handleQuickAction} />
      <ActionDispatcher />

      <div className="border-t border-brand-line p-2 sm:p-3">
        <AIChatInput
          value={inputValue}
          onChange={setInputValue}
          onSubmit={handleSend}
          disabled={isLoading}
        />
      </div>

      <div className="px-2 sm:px-3 pb-1 sm:pb-2 pt-1">
        <p className="text-center fluid-text-xs text-brand-muted">AI assistant • Not medical advice</p>
      </div>
    </div>
  );
}
