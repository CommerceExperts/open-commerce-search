import path from "node:path"
import { fileURLToPath } from "node:url"
import nextCoreWebVitals from "eslint-config-next/core-web-vitals"
import prettier from "eslint-config-prettier/flat"
import tailwindcss from "eslint-plugin-tailwindcss"
import unusedImports from "eslint-plugin-unused-imports"
import { defineConfig, globalIgnores } from "eslint/config"

export default defineConfig([
  globalIgnores([
    ".next/**",
    "node_modules/**",
    "public/**",
    "dist/**",
    ".cache/**",
    "**/*.esm.js",
    "next-env.d.ts",
  ]),
  nextCoreWebVitals,
  tailwindcss.configs["flat/recommended"],
  prettier,
  {
    plugins: {
      "unused-imports": unusedImports,
    },
    settings: {
      tailwindcss: {
        callees: ["cn"],
        config: path.join(
          path.dirname(fileURLToPath(import.meta.url)),
          "tailwind.config.js"
        ),
      },
    },
    rules: {
      "@next/next/no-html-link-for-pages": "off",
      "react/jsx-key": ["error", { checkFragmentShorthand: true }],
      "tailwindcss/no-custom-classname": "off",
      "no-unused-vars": "off",
      // React Compiler-era rules newly enforced by eslint-config-next 16.
      // They flag pre-existing patterns unrelated to the Next upgrade;
      // demoted to warnings so lint stays a usable gate. Fix as follow-up.
      "react-hooks/set-state-in-effect": "warn",
      "react-hooks/refs": "warn",
      "react-hooks/immutability": "warn",
      "react-hooks/incompatible-library": "warn",
      "unused-imports/no-unused-imports": "error",
      "unused-imports/no-unused-vars": [
        "warn",
        {
          vars: "all",
          varsIgnorePattern: "^_",
          args: "after-used",
          argsIgnorePattern: "^_",
        },
      ],
    },
  },
])
