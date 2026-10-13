#pragma once
#include "gguf.h"
#include <cstdint>
#include <stdexcept>
#include <string>

inline void validate_metadata(const gguf_context * meta, bool small, uint32_t allowed_ftype) {
    if (!meta) throw std::runtime_error("Métadonnées GGUF invalides");
    const auto arch = gguf_find_key(meta, "general.architecture");
    const auto type = gguf_find_key(meta, "general.file_type");
    if (arch < 0 || gguf_get_kv_type(meta, arch) != GGUF_TYPE_STRING ||
        std::string(gguf_get_val_str(meta, arch)) != "qwen3" || type < 0 ||
        gguf_get_kv_type(meta, type) != GGUF_TYPE_UINT32 || gguf_get_val_u32(meta, type) != allowed_ftype)
        throw std::runtime_error("Qwen3 GGUF Q4_K_M requis");
    const auto nt = gguf_get_n_tensors(meta);
    if (nt <= 0 || nt > 1000) throw std::runtime_error("Nombre de tenseurs refusé");
    uint64_t count = 0;
    const uint64_t maximum = small ? 850'000'000 : 2'100'000'000;
    const uint64_t minimum = small ? 400'000'000 : 1'300'000'000;
    for (int64_t i = 0; i < nt; ++i) {
        const auto * ne = gguf_get_tensor_ne(meta, i);
        uint64_t elements = 1;
        for (int d = 0; d < GGML_MAX_DIMS; ++d) {
            if (ne[d] <= 0 || static_cast<uint64_t>(ne[d]) > maximum / elements)
                throw std::runtime_error("Dimensions du modèle refusées");
            elements *= ne[d];
        }
        if (elements > maximum - count) throw std::runtime_error("Modèle trop grand pour le profil déclaré");
        count += elements;
    }
    if (count < minimum) throw std::runtime_error("Modèle incompatible avec le profil déclaré");
}
