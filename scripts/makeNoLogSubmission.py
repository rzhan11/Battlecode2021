import sys
import os
import shutil
# todo add
with open("nolog_keywords.txt") as f:
    commentWords = [l.strip() for l in f.readlines()]
print("commentWords", commentWords)

def shouldComment(line):
    stripped = line.strip()
    for w in commentWords:
        if stripped.startswith(w):
            return True
    return False

if len(sys.argv) != 2:
    print("Needs exactly 1 argument (e.g. 'examplefuncsplayer')")
    exit()

package = sys.argv[1]
new_package = package + "_nolog"

my_folder = os.path.dirname(os.path.realpath(__file__))
src_folder = my_folder + "/../src/" + package

print("Using: " + src_folder)
if not os.path.isdir(src_folder):
    print("Cannot find folder " + src_folder)
    exit()

dest_folder = src_folder + "_nolog"
if os.path.isdir(dest_folder):
    shutil.rmtree(dest_folder)
    while os.path.exists(dest_folder):
        pass
os.mkdir(dest_folder)

files = [f for f in os.listdir(src_folder) if os.path.isfile(os.path.join(src_folder, f))]
for file_name in files:
    src_file = os.path.join(src_folder, file_name)
    dest_file = os.path.join(dest_folder, file_name)
    print(src_file)
    print(dest_file)

    with open(src_file, "r") as src:
        src_lines = src.readlines()
    dest_lines = src_lines
    for i in range(len(dest_lines)):
        # check if package needs to be changed
        if dest_lines[i].startswith("import") or dest_lines[i].startswith("package"):
            dest_lines[i] = dest_lines[i].replace(package, new_package)
        if file_name != "Debug.java" and shouldComment(dest_lines[i]):
            dest_lines[i] = "////// " + dest_lines[i]
    with open(dest_file, "w") as f:
        f.writelines(dest_lines)
