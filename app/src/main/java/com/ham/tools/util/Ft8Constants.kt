package com.ham.tools.util

/**
 * FT8 协议的固定常量（LDPC(174,91) 生成矩阵与稀疏校验矩阵）。
 *
 * 数据来源与许可说明：
 * - 矩阵数值出自 K1JT 等《The FT4 and FT8 Communication Protocols》(QEX 2019)
 *   的配套资源包 ft4_ft8_protocols.tgz 里的 generator.dat / parity.dat，
 *   论文第 9 节明确把这部分数据置于公有领域（public domain），排除在 WSJT-X 的 GPLv3 之外。
 * - 具体的字节紧凑表示参考了 YL3JG 的 ft8_lib（constants.c）与 BG7YOZ 的 FT8CN（MIT）。
 *   本项目只采用「协议事实数据」与公开参数，所有编码/解码算法均为独立实现，未复制第三方源码。
 * - 帧结构、Costas 序列、Gray 映射、CRC 多项式同样来自上述公开协议文档。
 *
 * 纯 Kotlin，无 Android 依赖，可在 JVM 单测中直接验证。
 */
internal object Ft8Constants {

    const val LDPC_N = 174          // 码字位数
    const val LDPC_K = 91           // 77 位信息 + 14 位 CRC
    const val LDPC_M = 83           // 校验位 / 校验方程数
    const val PAYLOAD_BITS = 77
    const val CRC_BITS = 14
    const val FRAME_SYMBOLS = 79    // S7 D29 S7 D29 S7
    const val DATA_SYMBOLS = 58     // 174 / 3
    const val CRC_POLY = 0x6757     // x^14 项隐含
    const val TONE_SPACING_HZ = 6.25
    const val SYMBOL_SECONDS = 0.16
    const val MAXGRID4 = 32400      // 18*18*10*10

    const val NTOKENS = 2_063_592L
    const val MAX22 = 4_194_304L

    /** 7×7 Costas 数组的音调序列，帧首、帧中、帧尾各出现一次。 */
    val COSTAS = intArrayOf(3, 1, 4, 0, 6, 5, 2)

    /** 3bit 数值 -> 频道音调（Gray 编码）。 */
    val GRAY = intArrayOf(0, 1, 3, 2, 5, 6, 4, 7)

    /** 频道音调 -> 3bit 数值，由 GRAY 反推，避免手写一张可能抄错的表。 */
    val GRAY_INV: IntArray = IntArray(8).also { inv ->
        GRAY.forEachIndexed { value, tone -> inv[tone] = value }
    }

    /** 83 行 × 12 字节，MSB first，只有前 91 位有效（末字节低 5 位为填充 0）。 */
    private const val GENERATOR_HEX = """
8329ce11bf31eaf509f27fc0
761c264e25c2593354931320
dc265902fb277c6410a1bdc0
1b3f417858cd2dd33ec7f620
09fda4fee04195fd034783a0
077cccc11b8873ed5c3d48a0
29b62afe3ca036f4fe1a9da0
6054faf5f35d96d3b0c8c3e0
e20798e4310eed27884ae900
775c9c08e80e26ddae563180
b0b811028c2bf997213487c0
18a0c9231fc60adf5c5ea320
76471e8302a0721e01b12b80
ffbccb80ca8341fafb47b2e0
66a72a158f9325a2bf671700
c4243689fe85b1c51363a180
0dff739414d1a1b34b1c2700
15b48830636c8b99894972e0
29a89c0d3de81d665489b0e0
4f126f37fa51cbe61bd6b940
99c47239d0d97d3c84e09400
1919b75119765621bb4f1e80
09db12d731faee0b86df6b80
488fc33df43fbdeea4eafb40
827423ee40b675f756eb5fe0
abe197c484cb74757144a9a0
2b500e4bc0ec5a6d2bdbdd00
c474aa53d702187616693600
8eba1a13db3390bd6718cec0
753844673a27782cc42012e0
06ff83a145c37035a5c12680
3b37417858cc2dd33ec3f620
9a4a5a28ee17ca9c324842c0
bc29f465309c977e89610a40
2663ae6ddf8b5ce2bb294880
46f231efe457034c18144180
3fb2ce85abe9b0c72e06fbe0
de87481f282c153971a0a2e0
fcd7ccf23c69fa99bba14120
f0261447e9490ca8e474cec0
4410115818196f95cdd70120
088fc31df4bfbde2a4eafb40
b8fef1b6307729fb0a078c00
5afea7acccb77bbc9d99a900
49a7016ac653f65ecdc90760
1944d085be4e7da8d6cc7d00
251f62adc4032f0ee7140020
56471f8702a0721e00b12b80
2b8e4923f2dd51e2d537fa00
6b550a40a66f4755de95c260
a18ad28d4e27fe92a4f6c840
10c2e586388cb82a3d807580
ef34a41817ee02133db2eb00
7e9c0c54325a9c15836e0000
3693e572d1fde4cdf079e860
bfb2cec5abe1b0c72e07fbe0
7ee18230c583cccc57d4b080
a066cb2fedafc9f526641260
bb23725abc47cc5f4cc4cd20
ded9dba3bee40c59b5609b40
d9a7016ac653e6decdc90360
9ad46aed5f707f280ab5fc40
e5921c77822587316d7d3c20
4f14da8242a8b86dca733520
8b8b507ad467d4441df770e0
22831c9cf1169467ad04b680
213b838fe2ae54c38ee71800
5d926b6dd71f085181a4e120
66ab79d4b29ee6e69509e560
958148682d748a38dd68baa0
b8ce020cf069c32a723ab140
f4331d6d461607e957527460
6da23ba424b9596133cf9c80
a636bcbc7b30c5fbeae67fe0
5cb0d86a07df654a9089a200
f11f106848780fc9ecdd80a0
1fbb5364fb8d2c9d730d5ba0
fcb86bc70a50c9d02a5d0340
a534433029eac15f322e34c0
c989d9c7c3d3b8c55d751300
7bb38b2f0186d46643ae9620
2644ebadeb44b9467d1f42c0
608cc857594bfbb55d696000
"""

    /**
     * 83 个稀疏校验方程：每行列出参与异或的码字位（1-origin，0 表示该行只有 6 位）。
     * 这是 LDPC 置信传播解码必需的稀疏 H 矩阵，无法由生成矩阵直接替代。
     */
    private const val CHECK_ROWS = """
4 31 59 91 92 96 153
5 32 60 93 115 146 0
6 24 61 94 122 151 0
7 33 62 95 96 143 0
8 25 63 83 93 96 148
6 32 64 97 126 138 0
5 34 65 78 98 107 154
9 35 66 99 139 146 0
10 36 67 100 107 126 0
11 37 67 87 101 139 158
12 38 68 102 105 155 0
13 39 69 103 149 162 0
8 40 70 82 104 114 145
14 41 71 88 102 123 156
15 42 59 106 123 159 0
1 33 72 106 107 157 0
16 43 73 108 141 160 0
17 37 74 81 109 131 154
11 44 75 110 121 166 0
45 55 64 111 130 161 173
8 46 71 112 119 166 0
18 36 76 89 113 114 143
19 38 77 104 116 163 0
20 47 70 92 138 165 0
2 48 74 113 128 160 0
21 45 78 83 117 121 151
22 47 58 118 127 164 0
16 39 62 112 134 158 0
23 43 79 120 131 145 0
19 35 59 73 110 125 161
20 36 63 94 136 161 0
14 31 79 98 132 164 0
3 44 80 124 127 169 0
19 46 81 117 135 167 0
7 49 58 90 100 105 168
12 50 61 118 119 144 0
13 51 64 114 118 157 0
24 52 76 129 148 149 0
25 53 69 90 101 130 156
20 46 65 80 120 140 170
21 54 77 100 140 171 0
35 82 133 142 171 174 0
14 30 83 113 125 170 0
4 29 68 120 134 173 0
1 4 52 57 86 136 152
26 51 56 91 122 137 168
52 84 110 115 145 168 0
7 50 81 99 132 173 0
23 55 67 95 172 174 0
26 41 77 109 141 148 0
2 27 41 61 62 115 133
27 40 56 124 125 126 0
18 49 55 124 141 167 0
6 33 85 108 116 156 0
28 48 70 85 105 129 158
9 54 63 131 147 155 0
22 53 68 109 121 174 0
3 13 48 78 95 123 0
31 69 133 150 155 169 0
12 43 66 89 97 135 159
5 39 75 102 136 167 0
2 54 86 101 135 164 0
15 56 87 108 119 171 0
10 44 82 91 111 144 149
23 34 71 94 127 153 0
11 49 88 92 142 157 0
29 34 87 97 147 162 0
30 50 60 86 137 142 162
10 53 66 84 112 128 165
22 57 85 93 140 159 0
28 32 72 103 132 166 0
28 29 84 88 117 143 150
1 26 45 80 128 147 0
17 27 89 103 116 153 0
51 57 98 163 165 172 0
21 37 73 138 152 169 0
16 47 76 130 137 154 0
3 24 30 72 104 139 0
9 40 90 106 134 151 0
15 58 60 74 111 150 163
18 42 79 144 146 152 0
25 38 65 99 122 160 0
17 42 75 129 170 172 0
"""

    private fun lines(text: String): List<String> =
        text.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** 每行 12 字节，共 83 行。 */
    val GENERATOR: Array<ByteArray> by lazy {
        lines(GENERATOR_HEX).map { row ->
            require(row.length == 24) { "LDPC 生成矩阵行长度错误: ${row.length}" }
            ByteArray(12) { i -> row.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        }.toTypedArray().also {
            require(it.size == LDPC_M) { "生成矩阵行数错误: ${it.size}" }
        }
    }

    /** 每个校验方程涉及的码字位（0-origin）。 */
    val CHECKS: Array<IntArray> by lazy {
        lines(CHECK_ROWS).map { row ->
            row.split(Regex("\\s+"))
                .map { it.toInt() }
                .filter { it > 0 }
                .map { it - 1 }
                .toIntArray()
        }.toTypedArray().also {
            require(it.size == LDPC_M) { "校验方程行数错误: ${it.size}" }
            require(it.all { row -> row.size == 6 || row.size == 7 }) { "校验方程权重异常" }
        }
    }

    /** 每个码字位参与的 3 个校验方程（列重 3），由 CHECKS 推导，不额外维护一张易抄错的表。 */
    val BIT_CHECKS: Array<IntArray> by lazy {
        Array(LDPC_N) { bit ->
            CHECKS.indices.filter { c -> CHECKS[c].contains(bit) }.toIntArray()
        }.also {
            require(it.all { row -> row.size == 3 }) { "列重不是 3，矩阵数据可能有误" }
        }
    }
}
