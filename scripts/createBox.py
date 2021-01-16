r = 2
seen = set()

for i in range(-r, r + 1):
    for j in range(-r, r + 1):
        if max(abs(i), abs(j)) == 2:
            seen.add((i, j))

# print(seen)
print(len(seen))
seen = list(seen)

print("{", end="")
for i in range(len(seen)):
    if i > 0:
        print(",", end="")
    print("{" + str(seen[i][0]) + "," + str(seen[i][1]) + "}", end="")
print("}", end="")

print()
