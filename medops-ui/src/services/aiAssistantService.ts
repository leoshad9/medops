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
}

/** Wire format returned by `POST /api/v1/assistant/chat`. */
interface AssistantChatResponseDto {
  message: string;
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

  return {
    message: {
      id: crypto.randomUUID(),
      role: "assistant",
      content: response.data.data.message,
      timestamp: new Date().toISOString(),
    },
  };
}
