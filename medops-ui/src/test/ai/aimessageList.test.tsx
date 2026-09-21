import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";

import { AIMessageList } from "../../components/ai/AIMessageList";
import type { AIMessage } from "../../services/aiAssistantService";

describe("AIMessageList", () => {
  it("renders Markdown bold as <strong> instead of literal asterisks", () => {
    const messages: AIMessage[] = [
      {
        id: "1",
        role: "assistant",
        content: "**Doctor:** Dr. Mohd Adnan",
        timestamp: "2026-09-20T00:00:00Z",
      },
    ];

    const html = renderToStaticMarkup(
      <AIMessageList messages={messages} onQuickAction={() => {}} />
    );

    expect(html).toMatch(/<strong[^>]*>Doctor:<\/strong>/);
    expect(html).not.toContain("**Doctor:**");
  });

  it("renders Markdown bullet lists as <ul>/<li> with bold items", () => {
    const messages: AIMessage[] = [
      {
        id: "1",
        role: "assistant",
        content: "- **Doctor:** Dr. Mohd Adnan\n- **Location:** General OPD, Ground Floor\n- **Status:** Booked",
        timestamp: "2026-09-20T00:00:00Z",
      },
    ];

    const html = renderToStaticMarkup(
      <AIMessageList messages={messages} onQuickAction={() => {}} />
    );

    expect(html).toContain("<ul");
    expect(html).toContain("<li");
    expect(html).toMatch(/<strong[^>]*>Doctor:<\/strong>/);
    expect(html).not.toContain("**Doctor:**");
  });
});
