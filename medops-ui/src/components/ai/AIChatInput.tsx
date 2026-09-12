import { Send } from "lucide-react";
import type { KeyboardEvent } from "react";

interface AIChatInputProps {
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
  disabled?: boolean;
}

/** Renders the AI assistant text input and its send control. */
export function AIChatInput({ value, onChange, onSubmit, disabled = false }: Readonly<AIChatInputProps>) {
  /** Submits non-empty input when the user presses Enter. */
  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Enter" && !disabled && value.trim()) {
      event.preventDefault();
      onSubmit();
    }
  }

  return (
    <div className="flex items-center gap-2">
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={handleKeyDown}
        disabled={disabled}
        placeholder="Ask MedOps anything..."
        className="flex-1 rounded-xl border border-brand-line bg-brand-paper px-4 py-2.5 text-sm text-brand-ink placeholder-brand-muted focus:border-brand-primary focus:outline-hidden focus:ring-1 focus:ring-brand-primary"
      />
      <button
        type="button"
        onClick={onSubmit}
        disabled={disabled || !value.trim()}
        className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-primary text-white transition hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-50"
        aria-label="Send message"
      >
        <Send className="h-4 w-4" />
      </button>
    </div>
  );
}
