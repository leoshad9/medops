import { beforeEach, describe, expect, it, vi } from "vitest";

import { api } from "../../services/api";
import { getAIResponse } from "../../services/aiAssistantService";

// The real axios client wires CSRF cookies and a 401-retry interceptor; none of
// that is relevant here. Replace it with a stub that records the request body.
vi.mock("../../services/api", () => ({
  api: {
    post: vi.fn(),
  },
}));

const mockedPost = api.post as unknown as ReturnType<typeof vi.fn>;

describe("getAIResponse", () => {
  beforeEach(() => {
    mockedPost.mockReset();
    mockedPost.mockResolvedValue({
      data: { data: { message: "reply" } },
    });
  });

  it("sends the caller's IANA timeZone in the chat request body", async () => {
    await getAIResponse("When is my next appointment?", "Asia/Kolkata");

    expect(mockedPost).toHaveBeenCalledWith(
      "/v1/assistant/chat",
      expect.objectContaining({
        message: "When is my next appointment?",
        timeZone: "Asia/Kolkata",
      }),
    );
  });

  it("omits timeZone from the body when the caller does not supply one", async () => {
    await getAIResponse("hello");

    expect(mockedPost).toHaveBeenCalledWith("/v1/assistant/chat", { message: "hello" });
  });
});