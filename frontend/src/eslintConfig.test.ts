// @vitest-environment node
import { ESLint } from 'eslint'
import { expect, test } from 'vitest'

/** La configuration réelle du projet refuse dangerouslySetInnerHTML (spec §12.4, §13.7). */
test('react/no-danger is an error', { timeout: 30_000 }, async () => {
  const code = 'export const Probe = () => <div dangerouslySetInnerHTML={{ __html: "<b>x</b>" }} />\n'

  const [result] = await new ESLint().lintText(code, { filePath: 'src/probe.tsx' })

  expect(result?.messages.map((message) => [message.ruleId, message.severity])).toContainEqual(['react/no-danger', 2])
})
