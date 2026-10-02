package com.example.data.model

data class SampleVideoItem(
    val id: String,
    val title: String,
    val category: String,
    val aspectRatio: AspectRatio,
    val durationSeconds: Int,
    val description: String,
    val badge: String,
    val initialSegments: List<SubtitleSegmentSeed>
)

data class SubtitleSegmentSeed(
    val startMs: Long,
    val endMs: Long,
    val chinese: String,
    val vietnamese: String
)

object SampleVideoRepository {
    val SAMPLES = listOf(
        SampleVideoItem(
            id = "sample_douyin_street_food",
            title = "Ẩm thực Trùng Khánh: Lẩu cay tê siêu cay",
            category = "Ẩm thực Douyin",
            aspectRatio = AspectRatio.VERTICAL_9_16,
            durationSeconds = 18,
            description = "Video ẩm thực đường phố Douyin sôi động với phụ đề chân màn hình & câu ngắn",
            badge = "HOT DOUYIN",
            initialSegments = listOf(
                SubtitleSegmentSeed(
                    startMs = 300,
                    endMs = 1200,
                    chinese = "哇！",
                    vietnamese = "Oa, thơm quá!"
                ),
                SubtitleSegmentSeed(
                    startMs = 1400,
                    endMs = 4200,
                    chinese = "今天带大家来尝尝重庆正宗九宫格老火锅！",
                    vietnamese = "Hôm nay mình dẫn mọi người đi thử lẩu 9 ô chuẩn vị Trùng Khánh nhé!"
                ),
                SubtitleSegmentSeed(
                    startMs = 4500,
                    endMs = 5600,
                    chinese = "快看！",
                    vietnamese = "Mau nhìn này!"
                ),
                SubtitleSegmentSeed(
                    startMs = 5800,
                    endMs = 8600,
                    chinese = "看看这个牛油红亮，麻辣鲜香扑鼻而来。",
                    vietnamese = "Nhìn nồi nước lẩu đỏ au béo ngậy này, mùi cay nồng thơm nức mũi luôn."
                ),
                SubtitleSegmentSeed(
                    startMs = 8900,
                    endMs = 12200,
                    chinese = "毛肚七上八下，脆爽弹牙，真的绝绝子！",
                    vietnamese = "Nhúng sách bò đúng 7 lên 8 xuống, giòn sần sật, đỉnh nóc kịch trần thật sự!"
                ),
                SubtitleSegmentSeed(
                    startMs = 12400,
                    endMs = 13300,
                    chinese = "太绝了！",
                    vietnamese = "Quá đỉnh luôn!"
                ),
                SubtitleSegmentSeed(
                    startMs = 13500,
                    endMs = 15800,
                    chinese = "蘸上满满的蒜泥香油，一点都不燥辣。",
                    vietnamese = "Chấm ngập trong dầu mè tỏi phi, vừa thơm béo lại không hề gắt họng."
                ),
                SubtitleSegmentSeed(
                    startMs = 16000,
                    endMs = 18000,
                    chinese = "老铁们赶紧点赞收藏，下次一起来打卡！",
                    vietnamese = "Cả nhà nhớ bấm tim và lưu lại ngay, lần sau cùng ghé thử nha!"
                )
            )
        ),
        SampleVideoItem(
            id = "sample_tech_review",
            title = "Trên tay Smartphone gập thế hệ mới nhất",
            category = "Công nghệ Review",
            aspectRatio = AspectRatio.HORIZONTAL_16_9,
            durationSeconds = 20,
            description = "Video đánh giá công nghệ màn hình ngang 16:9 chuẩn YouTube Shorts & FB",
            badge = "16:9 TECH",
            initialSegments = listOf(
                SubtitleSegmentSeed(
                    startMs = 500,
                    endMs = 1400,
                    chinese = "来了！",
                    vietnamese = "Hàng về rồi đây!"
                ),
                SubtitleSegmentSeed(
                    startMs = 1600,
                    endMs = 5000,
                    chinese = "这款全新的折叠屏手机，上手质感完全颠覆我的想象。",
                    vietnamese = "Chiếc điện thoại màn hình gập mới này khi cầm trên tay thực sự vượt xa kỳ vọng của mình."
                ),
                SubtitleSegmentSeed(
                    startMs = 5300,
                    endMs = 6400,
                    chinese = "真的假的？",
                    vietnamese = "Thật hay đùa vậy?"
                ),
                SubtitleSegmentSeed(
                    startMs = 6600,
                    endMs = 9800,
                    chinese = "折痕处理得非常平整，展开几乎感觉不到它的存在。",
                    vietnamese = "Phần nếp gấp được xử lý siêu phẳng, mở ra hầu như không cảm nhận thấy vết hằn nào."
                ),
                SubtitleSegmentSeed(
                    startMs = 10100,
                    endMs = 14200,
                    chinese = "配备最新的高通旗舰芯片，玩大型游戏完全满帧流畅。",
                    vietnamese = "Được trang bị con chip Snapdragon đầu bảng mới nhất, chiến game nặng cực kỳ mượt mà."
                ),
                SubtitleSegmentSeed(
                    startMs = 14500,
                    endMs = 15500,
                    chinese = "太强了！",
                    vietnamese = "Quá mạnh mẽ!"
                ),
                SubtitleSegmentSeed(
                    startMs = 15700,
                    endMs = 19800,
                    chinese = "整体而言，这是目前我认为最具性价比的折叠旗舰。",
                    vietnamese = "Tổng kết lại, đây chính là mẫu flagship gập đáng đồng tiền bát gạo nhất hiện nay."
                )
            )
        ),
        SampleVideoItem(
            id = "sample_office_comedy",
            title = "Tình huống công sở: Ngày đầu làm việc",
            category = "Hài kịch Douyin",
            aspectRatio = AspectRatio.VERTICAL_9_16,
            durationSeconds = 17,
            description = "Clip hài kịch châm biếm công sở hài hước viral trên TikTok",
            badge = "HÀI HƯỚC",
            initialSegments = listOf(
                SubtitleSegmentSeed(
                    startMs = 400,
                    endMs = 1300,
                    chinese = "喂！",
                    vietnamese = "Alo!"
                ),
                SubtitleSegmentSeed(
                    startMs = 1500,
                    endMs = 4200,
                    chinese = "老板问我：你会使用各种办公软件吗？",
                    vietnamese = "Sếp hỏi tôi: Cậu có thành thạo các phần mềm văn phòng không?"
                ),
                SubtitleSegmentSeed(
                    startMs = 4400,
                    endMs = 5300,
                    chinese = "当然！",
                    vietnamese = "Dạ đương nhiên rồi!"
                ),
                SubtitleSegmentSeed(
                    startMs = 5500,
                    endMs = 8200,
                    chinese = "我自信满满地回答：那是相当熟练！",
                    vietnamese = "Tôi tự tin trả lời ngay: Dạ chuyện nhỏ, siêu thành thạo luôn ạ!"
                ),
                SubtitleSegmentSeed(
                    startMs = 8600,
                    endMs = 11800,
                    chinese = "结果上班第一天，连打印机电源在哪里都找不到。",
                    vietnamese = "Ai ngờ ngày đầu đi làm, đến cái công tắc máy in ở đâu tôi cũng không tìm ra."
                ),
                SubtitleSegmentSeed(
                    startMs = 12000,
                    endMs = 13100,
                    chinese = "尴尬！",
                    vietnamese = "Ngại chín mặt!"
                ),
                SubtitleSegmentSeed(
                    startMs = 13300,
                    endMs = 15400,
                    chinese = "同事都在憋笑，这尴尬得我能抠出三室一厅！",
                    vietnamese = "Đồng nghiệp ai cũng nhịn cười, ngại đến mức muốn độn thổ luôn các bác ạ!"
                ),
                SubtitleSegmentSeed(
                    startMs = 15600,
                    endMs = 17000,
                    chinese = "快救救孩子吧，太难了！",
                    vietnamese = "Ai cứu tôi với, cuộc sống người lớn khó quá đi!"
                )
            )
        )
    )
}
