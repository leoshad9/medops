import { Bot } from "lucide-react";

interface MedOpsAIFloatingButtonProps {
  onClick: () => void;
  hasUnread?: boolean;
}

/** Renders the AI assistant launcher with an optional unread indicator. */
export function MedOpsAIFloatingButton({ onClick, hasUnread = false }: Readonly<MedOpsAIFloatingButtonProps>) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="fixed bottom-6 right-6 z-40 flex h-14 w-14 items-center justify-center rounded-full bg-brand-primary text-white shadow-lg transition-all duration-200 hover:h-15 hover:w-15 hover:scale-105 hover:shadow-xl focus:outline-none focus:ring-2 focus:ring-brand-primary focus:ring-offset-2"
      aria-label="Open MedOps AI Assistant"
      title="Need help? Ask me!"
    >
      <Bot className="h-7 w-7" />
      {hasUnread && (
        <span className="absolute -top-1 -right-1 flex h-3.5 w-3.5 items-center justify-center rounded-full bg-brand-amber text-[9px] font-bold text-white">
          !
        </span>
      )}
    </button>
  );
}
