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

  it("renders GFM strikethrough as <del> instead of literal tildes", () => {
    const messages: AIMessage[] = [
      {
        id: "1",
        role: "assistant",
        content: "~~Cancelled~~ Appointment confirmed",
        timestamp: "2026-09-20T00:00:00Z",
      },
    ];

    const html = renderToStaticMarkup(
      <AIMessageList messages={messages} onQuickAction={() => {}} />
    );

    expect(html).toContain("<del");
    expect(html).not.toContain("~~Cancelled~~");
  });

  it("renders GFM tables correctly without leaking pipe characters", () => {
    const messages: AIMessage[] = [
      {
        id: "1",
        role: "assistant",
        content: "| Doctor | Location |\n|--------|----------|\n| Dr. Mohd Adnan | General OPD |",
        timestamp: "2026-09-20T00:00:00Z",
      },
    ];

    const html = renderToStaticMarkup(
      <AIMessageList messages={messages} onQuickAction={() => {}} />
    );

    expect(html).toContain("<table");
    expect(html).toContain("<th");
    expect(html).toContain("<td");
    expect(html).toContain("Dr. Mohd Adnan");
    expect(html).not.toMatch(/\|\s*Doctor\s*\|\s*Location/);
  });

  it("renders inline HTML from AI responses safely", () => {
    const messages: AIMessage[] = [
      {
        id: "1",
        role: "assistant",
        content: "Patient: John Doe<br>Age: 45",
        timestamp: "2026-09-20T00:00:00Z",
      },
    ];

    const html = renderToStaticMarkup(
      <AIMessageList messages={messages} onQuickAction={() => {}} />
    );

    expect(html).toContain("John Doe");
    expect(html).toContain("Age: 45");
    expect(html).toMatch(/<br\s*\/?>/);
  });

  it("sanitizes unsafe attributes and tags from inline HTML", () => {
    const messages: AIMessage[] = [
      {
        id: "1",
        role: "assistant",
        content:
          'Safe <strong>text</strong><br><span style="color: red" onclick="alert(1)">unsafe</span><script>alert(2)</script>',
        timestamp: "2026-09-20T00:00:00Z",
      },
    ];

    const html = renderToStaticMarkup(
      <AIMessageList messages={messages} onQuickAction={() => {}} />
    );

    expect(html).toMatch(/<strong[^>]*>text<\/strong>/);
    expect(html).toMatch(/<br\s*\/?>/);
    expect(html).not.toContain("style=");
    expect(html).not.toContain("onclick=");
    expect(html).not.toContain("<script");
    expect(html).not.toContain("alert(1)");
    expect(html).not.toContain("alert(2)");
  });

  it("renders bold text inside sentences without trailing asterisks", () => {
    const messages: AIMessage[] = [
      {
        id: "1",
        role: "assistant",
        content: "Your next appointment is with **Dr. Mohd Adnan** on **September 25**.",
        timestamp: "2026-09-20T00:00:00Z",
      },
    ];

    const html = renderToStaticMarkup(
      <AIMessageList messages={messages} onQuickAction={() => {}} />
    );

    expect(html).toMatch(/<strong[^>]*>Dr\. Mohd Adnan<\/strong>/);
    expect(html).toMatch(/<strong[^>]*>September 25<\/strong>/);
    expect(html).not.toContain("**Dr. Mohd Adnan**");
    expect(html).not.toContain("**September 25**");
  });
});
