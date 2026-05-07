import sys

with open('jettra-sender/src/main/java/io/jettra/fs/fx/JettraFileManagerFX.java', 'r') as f:
    lines = f.readlines()

start_idx = -1
end_idx = -1

for i, line in enumerate(lines):
    if "private void transferFileDirect(File s, File d, String fName" in line and not "            private void" in line:
        start_idx = i
        break

for i in range(start_idx, len(lines)):
    if "private void startTimer() {" in lines[i]:
        end_idx = i - 1
        while end_idx > 0 and lines[end_idx].strip() == "":
            end_idx -= 1
        break

print(f"Start: {start_idx}, End: {end_idx}")

lines = lines[:start_idx] + lines[end_idx+1:]

with open('jettra-sender/src/main/java/io/jettra/fs/fx/JettraFileManagerFX.java', 'w') as f:
    f.writelines(lines)
