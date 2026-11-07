// Metadata only: no Engine, llama_model_load_from_file, llama_init_from_model or sampler.
#include "model_check.h"
#include "native_configuration.h"
#include <cstdio>
#include <cstring>
#include <memory>
#include <string>
#include <vector>
#include <sys/resource.h>
int main(int argc,char **argv) {
  if(argc!=2)return 2;
  try {
    std::unique_ptr<gguf_context,decltype(&gguf_free)> meta(gguf_init_from_file(argv[1],{true,nullptr}),gguf_free);
    validate_metadata(meta.get(),true,LLAMA_FTYPE_MOSTLY_Q4_K_M);
    uint64_t parameters=0;
    for(int64_t i=0;i<gguf_get_n_tensors(meta.get());++i) {
      const auto *shape=gguf_get_tensor_ne(meta.get(),i);uint64_t n=1;
      for(int d=0;d<GGML_MAX_DIMS;++d)n*=shape[d];parameters+=n;
    }
    auto key=gguf_find_key(meta.get(),"tokenizer.chat_template");
    if(key<0 || gguf_get_kv_type(meta.get(),key)!=GGUF_TYPE_STRING)throw std::runtime_error("TEMPLATE_MISSING");
    const char *tmpl=gguf_get_val_str(meta.get(),key);
    if(std::strlen(tmpl)==0 || std::strlen(tmpl)>128*1024)throw std::runtime_error("TEMPLATE_SIZE");
    llama_chat_message messages[]={{"system","Public metadata-only template check. /no_think"},{"user","Bonjour"}};
    std::vector<char> text(128*1024);
    int rendered=llama_chat_apply_template(tmpl,messages,2,true,text.data(),text.size());
    if(rendered<=0 || rendered>int(text.size()))throw std::runtime_error("TEMPLATE_NOT_RECOGNIZED_BY_PINNED_LLAMA");
    Cancellation stop;auto cp=context_params(512,1,true,&stop);
    rusage usage{};if(getrusage(RUSAGE_SELF,&usage))throw std::runtime_error("METADATA_RSS_UNAVAILABLE");
    std::printf("{\"status\":\"PASS_METADATA_ONLY\",\"architecture\":\"qwen3\",\"file_type\":%d,\"tensor_count\":%lld,\"parameter_elements\":%llu,\"template_bytes\":%zu,\"template_format_supported\":true,\"template_probe_rendered_bytes\":%d,\"context\":%u,\"threads\":%d,\"batch\":%u,\"micro_batch\":%u,\"native_max_output\":%d,\"kv_type\":\"F16\",\"metadata_probe_peak_rss_bytes\":%lld,\"model_loaded\":false,\"tokenizer\":\"NOT_RUN\",\"resident_memory\":\"NOT_RUN\",\"generation\":\"NOT_RUN\"}\n",
      int(LLAMA_FTYPE_MOSTLY_Q4_K_M),(long long)gguf_get_n_tensors(meta.get()),(unsigned long long)parameters,std::strlen(tmpl),rendered,cp.n_ctx,cp.n_threads,cp.n_batch,cp.n_ubatch,maximum_output_tokens(true,512),(long long)usage.ru_maxrss*1024);
    return 0;
  }catch(const std::exception &e){std::fprintf(stderr,"METADATA_REJECTED: %s\n",e.what());return 1;}
}
