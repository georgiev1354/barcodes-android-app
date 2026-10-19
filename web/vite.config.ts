import { readFileSync } from "node:fs";
import { defineConfig } from "vitest/config";

const { version } = JSON.parse(readFileSync(new URL("./package.json", import.meta.url), "utf8")) as { version: string };

export default defineConfig({
  // Relative URLs, so the build works from any folder on the server, not just the domain root.
  base: "./",
  define: {
    __APP_VERSION__: JSON.stringify(version),
  },
  worker: {
    format: "es",
  },
  build: {
    target: "es2022",
  },
  server: {
    // Reachable from a phone on the same network (camera still needs HTTPS there, see docs).
    host: true,
  },
  test: {
    include: ["test/**/*.test.ts"],
  },
});
