import { beforeEach, describe, expect, it, vi } from "vitest";

import { api, prefetchCsrfToken } from "./api";

// NOTE: vite.config sets test.restoreMocks=true, which unwraps module-level
// vi.spyOn before each test. The spy must therefore be (re)created inside
// beforeEach, after the restore, or api.get calls bypass the mock.
let mockedGet: ReturnType<typeof vi.spyOn>;

function cookieForCsrf(present: boolean): void {
  Object.defineProperty(document, "cookie", {
    configurable: true,
    writable: true,
    value: present ? "XSRF-TOKEN=cookie-token" : "",
  });
}

describe("prefetchCsrfToken", () => {
  beforeEach(() => {
    mockedGet = vi.spyOn(api, "get").mockResolvedValue({ data: {} });
    cookieForCsrf(false);
  });

  it("coalesces concurrent prefetches into one /auth/csrf fetch", async () => {
    await Promise.all([prefetchCsrfToken(), prefetchCsrfToken()]);

    expect(mockedGet).toHaveBeenCalledTimes(1);
    expect(mockedGet).toHaveBeenCalledWith("/auth/csrf");
  });

  it("skips the fetch when the cookie already exists", async () => {
    cookieForCsrf(true);

    await prefetchCsrfToken();

    expect(mockedGet).not.toHaveBeenCalled();
  });

  it("swallows fetch failures so boot is never blocked", async () => {
    mockedGet.mockRejectedValueOnce(new Error("network down"));

    await expect(prefetchCsrfToken()).resolves.toBeUndefined();
  });
});
