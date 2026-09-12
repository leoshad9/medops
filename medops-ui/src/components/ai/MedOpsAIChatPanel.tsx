import { Bot, X } from "lucide-react";
import { useState } from "react";

import { AIChatInput } from "./AIChatInput";
import { AIMessageList } from "./AIMessageList";
import type { AIMessage } from "../../services/aiAssistantService";
import { getAIResponse } from "../../services/aiAssistantService";

interface MedOpsAIChatPanelProps {
  isOpen: boolean;
  onClose: () => void;
  firstName?: string;
}

/** Renders the patient-facing AI assistant panel and manages its conversation. */
export function MedOpsAIChatPanel({ isOpen, onClose, firstName = "there" }: Readonly<MedOpsAIChatPanelProps>) {
  const [messages, setMessages] = useState<AIMessage[]>([]);
  const [inputValue, setInputValue] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  if (!isOpen) return null;

  /** Sends the query associated with a selected quick action. */
  const handleQuickAction = (_actionId: string, query: string) => {
    handleSendQuery(query);
  };

  /** Adds a patient query and the corresponding assistant response to the chat. */
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
      const response = await getAIResponse(query);
      setMessages((prev) => [...prev, response.message]);
    } catch {
      // Network/API failure — user-facing message shown below; backend logs the error
      const errorMessage: AIMessage = {
        id: crypto.randomUUID(),
        role: "assistant",
        content: "Sorry, I'm having trouble connecting. Please try again later.",
        timestamp: new Date().toISOString(),
      };
      setMessages((prev) => [...prev, errorMessage]);
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
    <div className="fixed inset-y-0 right-0 z-50 flex h-dvh w-full max-w-md flex-col overflow-hidden border-l border-brand-line bg-white shadow-xl animate-in slide-in-from-right">
      <div className="flex items-center justify-between border-b border-brand-line p-4">
        <div className="flex items-center gap-2">
          <Bot className="h-5 w-5 text-brand-primary" />
          <h2 className="text-sm font-bold text-brand-ink">MedOps AI Assistant</h2>
        </div>
        <button
          type="button"
          onClick={handleClose}
          disabled={isLoading}
          className="flex h-7 w-7 items-center justify-center rounded-lg text-brand-muted hover:bg-brand-paper hover:text-brand-ink disabled:cursor-not-allowed"
          aria-label="Close chat"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      <AIMessageList messages={displayMessages} onQuickAction={handleQuickAction} />

      <div className="border-t border-brand-line p-3">
        <AIChatInput
          value={inputValue}
          onChange={setInputValue}
          onSubmit={handleSend}
          disabled={isLoading}
        />
      </div>

      <div className="px-3 pb-2 pt-1">
        <p className="text-center text-xs text-brand-muted">AI assistant • Not medical advice</p>
      </div>
    </div>
  );
}
