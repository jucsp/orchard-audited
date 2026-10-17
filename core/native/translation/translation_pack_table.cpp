/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Orchard.
 *
 * Orchard is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Orchard is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Orchard. If not, see <https://www.gnu.org/licenses/>.
 */

#include "translation_pack_table.h"

namespace {
#define ORCHARD_HELSINKI(code, rev) "https://huggingface.co/Helsinki-NLP/opus-mt_tiny_" code "-eng/resolve/" rev "/", rev
constexpr const char *kHelsinkiCredit = "OPUS-MT tiny by Helsinki-NLP (Apache-2.0)";

// Pinned commits plus hashes: a moved upstream file fails verification instead of loading.
// Only Helsinki-NLP packs: the fork never downloads from the upstream author's repositories (INV-3).
constexpr TranslationPack kPacks[] = {
    {"kor-eng", "kor", "standard", ORCHARD_HELSINKI("kor", "755c263bce01f7b1aa9472b32940961fa1efccf3"),
     {{"model.tflite", 20144264, "87407b100e072f252cb67b7065c362d0daf3d2b69275b2bb516143e0b4fbb586"},
      {"source.spm", 819766, "b3306bd45c665df899441788138d4735094d22cf3027dd81d71bb4f36d012d3f"},
      {"vocab.json", 899018, "725cafb1962cf3abce71d2596543cbebed3029f044fe132cd6a50e6cf88245e2"}},
     kHelsinkiCredit},
    {"zho-eng", "zho", "standard", ORCHARD_HELSINKI("zho", "4ee3a9342aae4ca6fbae82c930b5dccff5dd68dd"),
     {{"model.tflite", 20144264, "dcca1b5f98af1787cf7047799bd446bffaadb04246d706fb19867555e3de5eeb"},
      {"source.spm", 755175, "e5528a5c3685d08f8868902d31febeb763eea8519f9997f43b05964b02554640"},
      {"vocab.json", 776356, "13690d04e27310a43f0d879dfd38823c317e9ac69f436754baffbb0da6fc9dbe"}},
     kHelsinkiCredit},
    {"rus-eng", "rus", "standard", ORCHARD_HELSINKI("rus", "795b0b2fb13d17b917287421c15e0d70db9ba940"),
     {{"model.tflite", 20144264, "9b44353a63ccdd53253949a9321a280d6fa7e355021062d225b0c7158cba0879"},
      {"source.spm", 956176, "e684e7edf0c2a134665ac393e09e342175c7f909549e7aa0cbf0ad27390729f1"},
      {"vocab.json", 1455694, "3dfeb79f213886598ff9dd61b1ac87f068f1f729f5a4536829c8468994f57eb4"}},
     kHelsinkiCredit},
    {"ara-eng", "ara", "standard", ORCHARD_HELSINKI("ara", "4f9a7d50764299154b8c09ca0a44e9835580b92b"),
     {{"model.tflite", 20144264, "12a8d433c040053d7bbf85eca7693816ab1e5fa2a5f580a251690ade77f53597"},
      {"source.spm", 873174, "a332c963a3670b24bd3486632f5e9d3f9fa9b447556a8c944180f58e786f6a73"},
      {"vocab.json", 1196381, "1d9083d56ef6aeb9428ffe549e813b287d907c7fca825e6de23f6b30faad0a4d"}},
     kHelsinkiCredit},
    {"ell-eng", "ell", "standard", ORCHARD_HELSINKI("ell", "beff0d92479a72deff22f722ab28efa81d2ac8a9"),
     {{"model.tflite", 20144264, "245bc09ba6942a12d13d44f04c94bbd7ecc96ba9cfa7a5108c82b2db61c55698"},
      {"source.spm", 930710, "0935c946038cf67e3aa01a137d8ed4d13504ec4066d87083377272a80ec36e1e"},
      {"vocab.json", 1370651, "df58d10d4d7e1ce0671deac147f2b45638e3c1d1cccbf8c438e7145081878a6c"}},
     kHelsinkiCredit},
    {"tur-eng", "tur", "standard", ORCHARD_HELSINKI("tur", "2deaeba77ed2b5e6d1ae2a27b6169b2db2d7f2cb"),
     {{"model.tflite", 20144264, "da9c7fc86d95296ebfc6ed44cc0122b45175a0fe625467b80f57213c28a93a6c"},
      {"source.spm", 828303, "514637f7fbe60aaf50758d3b57dae3a57ed683bd30b6996c719a7c1b8c813641"},
      {"vocab.json", 845381, "492e7b7158a842e1e7d5272253cac951cfafba81b92b78b78168d07722f66ca7"}},
     kHelsinkiCredit},
    {"fra-eng", "fra", "standard", ORCHARD_HELSINKI("fra", "2e635daccd920c96ead1e72aa7601595022317ee"),
     {{"model.tflite", 20144264, "e85621db9b85f8c1e6f946353d44c8df5355dd350fce5299e28546b71596a818"},
      {"source.spm", 817787, "878ba36f64bf4025f296526ce725521839008fb5b69566b10e859e8617601421"},
      {"vocab.json", 792743, "4370436d3bcb91ca7f00b402a905248b9eb7535051af8ba54b8ba533b56e9f6c"}},
     kHelsinkiCredit},
    {"deu-eng", "deu", "standard", ORCHARD_HELSINKI("deu", "d120fdfccd7ba4fadabdc8aba72b86acd473e2b2"),
     {{"model.tflite", 20144264, "00339c387b2b7f1c425e660f43b3b182df7694c659ece9018074fa031bb48189"},
      {"source.spm", 813654, "b0c0d8de2d7f020afbfe2c9bd86c60cafc340b3d0cf45b969ab13313c462e0fb"},
      {"vocab.json", 774615, "8983a5be7ba67df7e376c34a8041f65663f78517bbd270af0c9ad703e7169b1d"}},
     kHelsinkiCredit},
    {"spa-eng", "spa", "standard", ORCHARD_HELSINKI("spa", "ca15fbd0a8412a473b95e010ad54429a7a34a7ba"),
     {{"model.tflite", 20144264, "27f152f706a2efea296031a7962448a52cd61492467fe232f950851a0240077c"},
      {"source.spm", 819359, "a40ad94d67f431248d5dec9c7f0ded19efce671568ff07c959ee58ca50590265"},
      {"vocab.json", 788609, "a2efd26a550481379f0aafbf2b6252914fd20e537457a258f830d05dd7a8e2c2"}},
     kHelsinkiCredit},
    {"ita-eng", "ita", "standard", ORCHARD_HELSINKI("ita", "ac17655796ab9cd2f5916e3f454c9d09d9ea3a87"),
     {{"model.tflite", 20144264, "f8a9b69dfd4f5bcdbc889c2e9a71ee16d5b715e9c8a9738109db362fc8ce1bb1"},
      {"source.spm", 817363, "41026f577bfdf1b448d1383910a7878eee9b202202c972f8f13d7a8c4d1352d7"},
      {"vocab.json", 775633, "29025d02d70a44bb69283a58c7771648889d0431131aa36fc7b9d60eddaae165"}},
     kHelsinkiCredit},
    {"nld-eng", "nld", "standard", ORCHARD_HELSINKI("nld", "1d00eacd4a5d93382a60444a4c0f0d2ab46c9ddc"),
     {{"model.tflite", 20144264, "7677804d96828b5d89babfb996191b0330bc7c9fcdebaf741e3f08f2d7afc177"},
      {"source.spm", 810940, "9d0fb485b5d5fa554d8c30192a6a94ad729ed4bbfa3ff820b55eeec4e8ca8855"},
      {"vocab.json", 765325, "d9cb34092f3e7b04377b6429932a87347d9b1d9cda4ebdc34cdbadeb54a7379f"}},
     kHelsinkiCredit},
    {"cat-eng", "cat", "standard", ORCHARD_HELSINKI("cat", "a717a864a3452d0f6fdf8d9963e9d2741292ef1a"),
     {{"model.tflite", 20144264, "fddfeba8eb5ec3205b169133eea17ef89ad4538654ec648b7dcfd77f003350f5"},
      {"source.spm", 816709, "f39353ef7507738d1766fb4104ca93b316cc816497198c452433e991a1966971"},
      {"vocab.json", 784803, "6cd3ea3f3ecb3c7fda30aa2799984cbaa55f02ea0cb0f5a914c8e917249a3ad0"}},
     kHelsinkiCredit},
};
#undef ORCHARD_HELSINKI
} // namespace

std::span<const TranslationPack> translationPackTable() { return kPacks; }
