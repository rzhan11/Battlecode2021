from math import sqrt

DIRS = [(0, 1), (1, 1), (1, 0), (1, -1), (0, -1), (-1, -1), (-1, 0), (-1, 1)]

r2 = 20
r = int(sqrt(r2))
seen1 = set()

for i in range(-r, r + 1):
    for j in range(-r, r + 1):
        if i * i + j * j <= r2:
            seen1.add((i, j))

diffs = []

for dir in DIRS:
    seen2 = set()
    for i, j in seen1:
        if i * i + j * j <= r2:
            seen2.add((i + dir[0], j + dir[1]))

    diff = list(seen2 - seen1)
    diffs += [diff]

print("{", end="")
for i in range(len(diffs)):
    if i > 0:
        print(",", end="")
    print("{", end="")
    for j in range(len(diffs[i])):
        if j > 0:
            print(",", end="")
        print("{" + str(diffs[i][j][0]) + "," + str(diffs[i][j][1]) + "}", end="")
    print("}", end="")

print("}")
