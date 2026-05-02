package com.sharedbackpack.commands;

import java.util.*;

/**
 * Lightweight pinyin conversion for Chinese character search.
 * Covers common Minecraft-related characters and modded item names.
 */
public class PinyinUtil {

    // Map of common Chinese characters to their pinyin (without tone marks)
    private static final Map<Character, String> CHAR_TO_PINYIN = new HashMap<>();

    static {
        // Common MC-related characters
        add("木", "mu"); add("头", "tou"); add("石", "shi"); add("铁", "tie");
        add("金", "jin"); add("钻", "zuan"); add("剑", "jian"); add("斧", "fu");
        add("镐", "gao"); add("铲", "chan"); add("锄", "chu"); add("弓", "gong");
        add("箭", "jian"); add("盾", "dun"); add("甲", "jia"); add("盔", "kui");
        add("靴", "xue"); add("裤", "ku"); add("腿", "tui"); add("帽", "mao");
        add("衣", "yi"); add("服", "fu"); add("食", "shi"); add("物", "wu");
        add("品", "pin"); add("药", "yao"); add("水", "shui"); add("瓶", "ping");
        add("火", "huo"); add("把", "ba"); add("桶", "tong"); add("箱", "xiang");
        add("工", "gong"); add("具", "ju"); add("方", "fang"); add("块", "kuai");
        add("砖", "zhuan"); add("泥", "ni"); add("土", "tu"); add("沙", "sha");
        add("玻", "bo"); add("璃", "li"); add("煤", "mei"); add("炭", "tan");
        add("红", "hong"); add("蓝", "lan"); add("绿", "lv"); add("黄", "huang");
        add("黑", "hei"); add("白", "bai"); add("紫", "zi"); add("灰", "hui");
        add("粉", "fen"); add("橙", "cheng"); add("青", "qing"); add("棕", "zong");
        add("花", "hua"); add("草", "cao"); add("树", "shu"); add("叶", "ye");
        add("种", "zhong"); add("子", "zi"); add("苗", "miao"); add("果", "guo");
        add("苹", "ping"); add("萝", "luo"); add("卜", "bo"); add("麦", "mai");
        add("面", "mian"); add("粉", "fen"); add("包", "bao"); add("蛋", "dan");
        add("糕", "gao"); add("饼", "bing"); add("肉", "rou"); add("鱼", "yu");
        add("鸡", "ji"); add("牛", "niu"); add("羊", "yang"); add("猪", "zhu");
        add("兔", "tu"); add("马", "ma"); add("狗", "gou"); add("猫", "mao");
        add("狼", "lang"); add("熊", "xiong"); add("蜂", "feng"); add("蜜", "mi");
        add("书", "shu"); add("架", "jia"); add("附", "fu"); add("魔", "mo");
        add("台", "tai"); add("床", "chuang"); add("门", "men"); add("梯", "ti");
        add("栏", "lan"); add("杆", "gan"); add("栅", "zha"); add("围", "wei");
        add("墙", "qiang"); add("地", "di"); add("板", "ban"); add("楼", "lou");
        add("岩", "yan"); add("浆", "jiang"); add("末", "mo"); add("影", "ying");
        add("地", "di"); add("狱", "yu"); add("界", "jie"); add("末", "mo");
        add("龙", "long"); add("蛋", "dan"); add("凋", "diao"); add("零", "ling");
        add("骷", "ku"); add("髅", "lou"); add("僵", "jiang"); add("尸", "shi");
        add("苦", "ku"); add("力", "li"); add("怕", "pa"); add("爬", "pa");
        add("蜘", "zhi"); add("蛛", "zhu"); add("史", "shi"); add("莱", "lai");
        add("姆", "mu"); add("铁", "tie"); add("雪", "xue"); add("球", "qiu");
        add("荧", "ying"); add("光", "guang"); add("墨", "mo"); add("囊", "nang");
        add("线", "xian"); add("丝", "si"); add("绳", "sheng"); add("皮", "pi");
        add("革", "ge"); add("羽", "yu"); add("毛", "mao"); add("骨", "gu");
        add("粉", "fen"); add("染", "ran"); add("料", "liao"); add("颜", "yan");
        add("色", "se"); add("矿", "kuang"); add("冶", "ye"); add("炼", "lian");
        add("炉", "lu"); add("熔", "rong"); add("锻", "duan"); add("造", "zao");
        // TFC-related
        add("陶", "tao"); add("瓷", "ci"); add("铸", "zhu"); add("锭", "ding");
        add("板", "ban"); add("薄", "bo"); add("片", "pian"); add("焊", "han");
        add("接", "jie"); add("锻", "duan"); add("铁", "tie"); add("砧", "zhen");
        add("矿", "kuang"); add("脉", "mai"); add("岩", "yan"); add("石", "shi");
        add("安", "an"); add("山", "shan"); add("英", "ying"); add("白", "bai");
        add("玄", "xuan"); add("武", "wu"); add("花", "hua"); add("岗", "gang");
        add("闪", "shan"); add("长", "chang"); add("辉", "hui"); add("橄", "gan");
        add("榄", "lan"); add("砾", "li"); add("粘", "zhan"); add("黏", "nian");
        // Farming
        add("大", "da"); add("小", "xiao"); add("甘", "gan"); add("蔗", "zhe");
        add("稻", "dao"); add("米", "mi"); add("谷", "gu"); add("玉", "yu");
        add("黍", "shu"); add("高", "gao"); add("粱", "liang"); add("大", "da");
        add("豆", "dou"); add("豌", "wan"); add("豌", "dou"); add("辣", "la");
        add("椒", "jiao"); add("茄", "qie"); add("洋", "yang"); add("葱", "cong");
        add("蒜", "suan"); add("南", "nan"); add("瓜", "gua"); add("黄", "huang");
        add("西", "xi"); add("甜", "tian"); add("菜", "cai"); add("卷", "juan");
        add("心", "xin"); add("生", "sheng"); add("亚", "ya"); add("麻", "ma");
        add("棉", "mian"); add("黄", "huang"); add("麻", "ma"); add("蕉", "jiao");
        add("椰", "ye"); add("桃", "tao"); add("梨", "li"); add("柠", "ning");
        add("檬", "meng"); add("樱", "ying"); add("莓", "mei"); add("葡", "pu");
        add("萄", "tao"); add("番", "fan"); add("石", "shi"); add("榴", "liu");
        add("木", "mu"); add("瓜", "gua"); add("甘", "gan"); add("草", "cao");
        add("何", "he"); add("首", "shou"); add("乌", "wu"); add("人", "ren");
        add("参", "shen"); add("薄", "bo"); add("荷", "he"); add("姜", "jiang");
    }

    private static void add(String chinese, String pinyin) {
        for (char c : chinese.toCharArray()) {
            CHAR_TO_PINYIN.put(c, pinyin);
        }
    }

    /**
     * Convert a Chinese string to pinyin (without tones).
     * Unknown characters are kept as-is.
     */
    public static String toPinyin(String input) {
        if (input == null || input.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            String py = CHAR_TO_PINYIN.get(c);
            if (py != null) {
                sb.append(py);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Convert to pinyin initials (first letter of each character's pinyin).
     */
    public static String toPinyinInitials(String input) {
        if (input == null || input.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            String py = CHAR_TO_PINYIN.get(c);
            if (py != null) {
                sb.append(py.charAt(0));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
