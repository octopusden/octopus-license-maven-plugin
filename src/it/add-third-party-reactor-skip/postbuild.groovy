file = new File(basedir, 'target/generated-resources/licenses/THIRD-PARTY.txt');
assert file.exists();

content = file.text;
assert !content.contains('test-add-third-party-reactor-skip-child');
assert !content.contains('Unknown license');
assert content.contains('commons-logging:commons-logging:1.1.1');

return true;
