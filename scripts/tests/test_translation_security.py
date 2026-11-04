"""Exercise the translation shell boundary without network or external XML tooling."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'generateFilelist.sh'


class TranslationSecurityTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        binaries = self.root / 'bin'
        binaries.mkdir()
        (binaries / 'git').write_text(
            '#!/usr/bin/env python3\nimport sys\n'
            "assert sys.argv[1:] == ['diff', '-U0', 'HEAD~1', '--', 'app/src/main/res/values-en/strings.xml']\n"
            "print('+++ b/app/src/main/res/values-en/strings.xml')\n"
            "print('+<string name=\"sample\">$(touch MUST_NOT_EXIST) %1$s</string>')\n",
            encoding='utf-8')
        (binaries / 'xmlstarlet').write_text(
            '#!/usr/bin/env python3\nimport sys,xml.etree.ElementTree as E\n'
            "root = E.fromstring(sys.stdin.read())\n"
            "print('\\n'.join(''.join(value.itertext()) for value in root.findall('string')))\n",
            encoding='utf-8')
        for binary in binaries.iterdir():
            binary.chmod(0o700)
        self.environment = dict(os.environ, PATH=str(binaries) + os.pathsep + os.environ['PATH'])

    def run_helper(self, count, resource):
        return subprocess.run(['bash', str(SCRIPT), count, resource], cwd=self.root,
                              env=self.environment, text=True, capture_output=True, timeout=10)

    def test_translation_content_stays_literal_data(self):
        result = self.run_helper('1', 'app/src/main/res/values-en/strings.xml')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('$(touch MUST_NOT_EXIST)', result.stdout)
        self.assertNotIn('%1$s', result.stdout)
        self.assertFalse((self.root / 'MUST_NOT_EXIST').exists())

    def test_untrusted_revisions_and_paths_are_rejected_before_git(self):
        for count, resource in [
            ('1;touch MUST_NOT_EXIST', 'app/src/main/res/values-en/strings.xml'),
            ('0', 'app/src/main/res/values-en/strings.xml'),
            ('1', 'app/src/main/res/values-en/strings.xml$(touch MUST_NOT_EXIST)'),
            ('1', '--output=MUST_NOT_EXIST'),
        ]:
            with self.subTest(count=count, resource=resource):
                result = self.run_helper(count, resource)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse((self.root / 'MUST_NOT_EXIST').exists())


if __name__ == '__main__':
    unittest.main()
