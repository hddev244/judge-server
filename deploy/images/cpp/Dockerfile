FROM gcc:13
# gcc:13 đổi patch (13.4 → 13.x) — tìm stdc++.h thay vì hardcode đường dẫn.
RUN apt-get update && apt-get install -y --no-install-recommends time \
    && rm -rf /var/lib/apt/lists/* \
    && (getent passwd 1000 >/dev/null || useradd -u 1000 -m runner) \
    && STDC=$(find /usr/local/include/c++ -name 'stdc++.h' | head -n1) \
    && test -n "$STDC" \
    && GCH_DIR="$(dirname "$STDC")/stdc++.h.gch" \
    && mkdir -p "$GCH_DIR" \
    && g++ -O2 -std=c++17 "$STDC" -o "$GCH_DIR/O2_c++17.gch" \
    && g++ -O3 -std=c++17 -pipe -DONLINE_JUDGE "$STDC" -o "$GCH_DIR/O3_c++17_pipe.gch" \
    && g++ -O2 -std=c++17 -pipe -DONLINE_JUDGE "$STDC" -o "$GCH_DIR/O2_c++17_pipe.gch" \
    && g++ -O3 -std=c++20 -pipe -DONLINE_JUDGE "$STDC" -o "$GCH_DIR/O3_c++20_pipe.gch" \
    && g++ -O2 -std=c++20 "$STDC" -o "$GCH_DIR/O2_c++20.gch"
