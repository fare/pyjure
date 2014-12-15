def sieve(n):
  primes = [True] * (n+1)
  counter = 0
  for i in range(2,n):
    if primes[i]:
      counter = counter + 1
      for j in range(i*i, n, i):
        primes[j] = False
  return counter

import time
a = time.time()
print sieve(10000000)
b=time.time()
print b-a, 'seconds'