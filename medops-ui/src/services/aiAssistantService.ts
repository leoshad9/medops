import type { ApiResponse } from "../types/api";
import { api } from "./api";

export interface AIMessage {
  id: string;
  role: "assistant" | "user";
  content: string;
  timestamp: string;
}

export interface AIChatResponse {
  message: AIMessage;
  raw?: {
    actions?: Array<{ id: string | null; resolved?: any; label?: string | null } | null> | null;
    suggestions?: Array<string | { label?: string; query?: string }> | null;
  } | null;
}

/** Wire format returned by `POST /api/v1/assistant/chat`. */
interface AssistantChatResponseDto {
  message: string;
  actions?: Array<{ id: string | null; resolved?: any; label?: string | null } | null> | null;
}

/**
 * Sends a chat message to the MedOps assistant API and resolves with the reply.
 *
 * Identity is derived server-side from the authenticated session; this call
 * intentionally carries nothing but the message text. The optional `timeZone`
 * is the caller's IANA zone (e.g. `Intl.DateTimeFormat().resolvedOptions().timeZone`);
 * the backend renders appointment times in it. When omitted the server falls back to UTC.
 *
 * :throws: when the API is unreachable or the assistant backend fails —
 * callers show a user-facing error (see MedOpsAIChatPanel).
 */
export async function getAIResponse(query: string, timeZone?: string): Promise<AIChatResponse> {
  const response = await api.post<ApiResponse<AssistantChatResponseDto>>(
    "/v1/assistant/chat",
    timeZone ? { message: query, timeZone } : { message: query },
  );

  // Keep the raw actions array for the chat panel to populate the resolver.
  const dto = response.data.data;

  return {
    message: {
      id: crypto.randomUUID(),
      role: "assistant",
      content: dto.message,
      timestamp: new Date().toISOString(),
    },
    // expose the raw response for caller use (non-serialised)
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    raw: dto,
  } as unknown as AIChatResponse;
}
