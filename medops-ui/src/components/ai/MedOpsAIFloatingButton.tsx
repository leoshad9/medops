import { Bot } from "lucide-react";

interface MedOpsAIFloatingButtonProps {
  onClick: () => void;
  hasUnread?: boolean;
  className?: string;
}

/** Renders the AI assistant launcher with an optional unread indicator. */
export function MedOpsAIFloatingButton({ onClick, hasUnread = false, className = '' }: Readonly<MedOpsAIFloatingButtonProps>) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`fixed bottom-4 right-4 z-40 flex h-12 w-12 sm:h-14 sm:w-14 md:h-14 md:w-14 items-center justify-center rounded-full bg-brand-primary text-white shadow-lg transition-all duration-200 hover:scale-105 hover:shadow-xl focus:outline-none focus:ring-2 focus:ring-brand-primary focus:ring-offset-2 ${className}`}
      aria-label="Open MedOps AI Assistant"
      title="Need help? Ask me!"
    >
      <Bot className="h-6 w-6 sm:h-7 sm:w-7" />
      {hasUnread && (
        <span className="absolute -top-1 -right-1 flex h-3.5 w-3.5 items-center justify-center rounded-full bg-brand-amber text-[9px] font-bold text-white">
          !
        </span>
      )}
    </button>
  );
}
