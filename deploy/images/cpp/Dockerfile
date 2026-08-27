FROM gcc:13
RUN apt-get update && apt-get install -y --no-install-recommends time \
    && rm -rf /var/lib/apt/lists/* \
    && (getent passwd 1000 >/dev/null || useradd -u 1000 -m runner) \
    && mkdir -p /usr/local/include/c++/13.4.0/x86_64-linux-gnu/bits/stdc++.h.gch \
    && g++ -O2 -std=c++17 /usr/local/include/c++/13.4.0/x86_64-linux-gnu/bits/stdc++.h -o /usr/local/include/c++/13.4.0/x86_64-linux-gnu/bits/stdc++.h.gch/O2_c++17.gch \
    && g++ -O3 -std=c++17 -pipe -DONLINE_JUDGE /usr/local/include/c++/13.4.0/x86_64-linux-gnu/bits/stdc++.h -o /usr/local/include/c++/13.4.0/x86_64-linux-gnu/bits/stdc++.h.gch/O3_c++17_pipe.gch \
    && g++ -O2 -std=c++17 -pipe -DONLINE_JUDGE /usr/local/include/c++/13.4.0/x86_64-linux-gnu/bits/stdc++.h -o /usr/local/include/c++/13.4.0/x86_64-linux-gnu/bits/stdc++.h.gch/O2_c++17_pipe.gch \
    && g++ -O3 -std=c++20 -pipe -DONLINE_JUDGE /usr/local/include/c++/13.4.0/x86_64-linux-gnu/bits/stdc++.h -o /usr/local/include/c++/13.4.0/x86_64-linux-gnu/bits/stdc++.h.gch/O3_c++20_pipe.gch \
    && g++ -O2 -std=c++20 /usr/local/include/c++/13.4.0/x86_64-linux-gnu/bits/stdc++.h -o /usr/local/include/c++/13.4.0/x86_64-linux-gnu/bits/stdc++.h.gch/O2_c++20.gch


