"""Portable negative tests using genuine existing Gson shapes and pinned offline maps.

PINNED_DATA is compressed JSON copied from the source-qualified preparation:
full 533 member map and full actual Iris 368 option map. It is test data, never
an observation of a current JVM. Phase timings/artifact locators are synthetic.
"""
import base64
import copy
import hashlib
import json
from pathlib import Path
import sys
import unittest
import zlib

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from test_inprocess_control import fixture
from test_volume_inprocess_control import OBJECTS, WINDOW
import analyze_live_pixel_control as live

PINNED_DATA = (
    'c-p+Z$&#H%ww?JdIj)=ZzR^^62Qrg1C`nc|3-gx5O#p#H0GUbY<?CBN&Iv>SC;kWym}DjiqB;Ax`)<~9-_8H|%ZK%$ug?#E`A`4n'
    'FaPzw{O5oF@Bic9|MH*y5^V^^O<UHQ-L$=Y4nBm@>Nb0o+_SGW=NfIXCG@gK3d`G+LyU2c_?Q3dFAu-9xjw(Ypa1s$-QU;0eR}uL'
    '_5R=b*ZJ%E^4I@(xc`V0Uq)Petjxcsb-^^BcJ9lxu}Y66<{Zqdt?gcXpWB7HcK6%PnY<bGQOd(F{r&y;+h0F@zW=m7e|fi9&R_UL'
    'InFw^FTuy0c8SZ_I_pl=&1}nP-ggsHGqsxCM~ZcsRI6Ri_dcF{`uh0g-Iw2=fBbMOEIXU&KAF&bnaT9&Ox|g4pTe*qTFa*JKT}x@'
    'Un6_Fld-Lv)t-F(yASQx^-KHm;r`*%Z|n2=<zcihU)JY$AAkMpY+n#IL-Ap4-&-HnvzF@o_H~9DXR347nOVe+X|EoP_pxy-ZI_mM'
    ')#1Ed*k3=459>=qW{th2G3;I(YeHyCNUPE1umZ>A<{I9t89A9z`C6@oj7+nAZvKYMU)%fkPj>O+ui8G-^D=X-P@?0!TP<svm1k-p'
    '@xj3kXBSe+W(y}<ICf@tJFA>E#gk9}zJ!*;<V>ftZKTw_4Ra?KgKJjGVOU)X*3Fh|D5lii%`hgno>omI&Yy&QynpwwE-SzQTw89m'
    'Y2p~$phC;dVx6fjgym9Pe(<5I1XGgRp5Ieq4Z-i6S>Veb{wFTrANQZ%uix+b*WTa%eo-!8x0^1HjiBDU<F7I5a+`0N(YflI8F@|@'
    'Q(kE&KDZ|{5k1zPyS^^u&tK5PKi=uzmldUD{Wj5?;7aC<3T^}S@hyaEe6w@4oGqqrb1N%i@y8EC@3xy+-Xi7CS<=Vx{_9-7^sjsK'
    '-+%q}{lj}CIO7LO)?MVcjT&4ZD>nSjo>)<<+17?d=4zJ5S}V>n)>cwLY}j?*kg)H6#jDLb{(blN_g6u<-eRjOcITGnp27L){8+vF'
    'h;>@NbzH9dS%YtJ?%fi)J=2n=*fw7f^#0S|CFm+8WHuz0GIk0x7Pf3I+fy+qxY1W(O(ulmY^?*&V{MMf`W2%|G1!=1kaGX`^;zdM'
    'e`z1v``^F38&`oQl-|oQDfeJ^Gch-}ZQ@&ul5+AcvBNbt%l=hgt-(@znLc%X=i2L4f#d$;!<Y8)%j1bnK0idn^<gU8v_>|@^HHm{'
    'm6E(@ki%Pwcpg0=bOg5p4!%b3`l}K@wa*Rxd!i)7W{3BW)@-b`77TW-nb=?sXWGrd`r=|=F-@07H`YhPwzx3~jN82`@^kzBL;E~_'
    'Tk}ybIj?P98KK+K(Z<|xc>JB_Dt?cdQ(}LM&#St+J>L&C9UNv1dA0b0$cJ|yKD}R0HHICtS(cebD(eXUHfC35b156YU2ns#YCRjy'
    '`oShFjdS&h?+}Z5LC`-M8~B3>WI|0Iy%MXdO9_*n-i_KEvCsG6wN)Ln3kp%jn8d&PC<{EYY`D3NzrMfk?NLB*#(*4l!7JI-IhO+5'
    'aJ~U)nyr}l-m}__G0f;k9@gTI*+4lc@Wu@R@8A9U+ZW>ZFYW%9U%#~bN4+nlCSr0cE`xy3t%Z8nfWifb8>zLKTnvZ}Hy?p`sk9n)'
    'Pr(~@+rJ{};o-ZemDsjwUFI`OG%eKOMsgNd<cKb9;}228G5u(qB!P0-IQ9@G3xt<5QT<`=s~lhgmgKU-GjzW~-uU*x-ltSEp*8Z~'
    '74!)D>Vzk38C}^HOc8deww%cs@9)3PJN@$gKfarloFIqNH)n%sCVWKh(coiy1+~}(kn}q>!pi`mPQoRzr1Q&rSQAeL-oL-UV%Os*'
    '?~b3jipD|oxGT^av>IwQ$;h08Z5v0d1JkA^bOozUju@8#x0`P@6yE9^L@NY5lj(g$x5H{>*A4=#xoC~-`2cxZ1qF)*IBOd5&BX;e'
    'k8PvPgpGPL)ch#(#!6ES&<gz0#QkqRzRg@Lo~yI1ASwJJxE6n9Tf!yJjtgmg!2y=yyV^HM{kXn>cAtw~V42#Z1ITsZ>lxuUE@phk'
    '4)6;*8gYSGTENxXRv)X$1aW*9KyJQ4>_1lfS#Gs~UBg>u&a+PZ*9JGP45-HLMzqbw=FBJ)PO>XRY&1s@?j@DHLO7E<-|hYNVclJS'
    ';&H73#|ERCzyxqMVbxV|$OKojl)*;VHbPvs`Pm7xGhjV{iya!jW?qr`d5tgc?g_i!<bG(M9t~(u5kd=)7{Fq-E)mgtBlNoHEXXAl'
    'xr$`*vojyg6yOn3o)~d#r-FZLALnoD{rqXQ_|6aqe>xS&neh>HW@jABd%5&hY=E0(Uw|M!js`wRU^wtPo)Y|aCKNRD#AL4$fiZn%'
    'LLhu~X2)V;g9wgAoM-KO&1nZfI>DB9#S2=t9`_DGTm0tyn#BG7{(kOv@85rqi7bU(T9F8DT$%<totv2(<A;ek`E`y?&R^^d+8fI3'
    '1W(`&FzYpePt5f3EPDgCRBWav`P&6Y1Hw%CR(M)AJ8s*zFtrPK<cK)C1B?wIg*J`XWd8mBpX>d*|J|fZPhvk2$<|^C`b1p3ZJ4R='
    '1^Md64Fj5tLohB44jPUMUF;szA3V;<oyolTdW`kMhwl<G4?Yy23qBhV+M*q1nRV2H|Emo=x>MO5FcYRO0kQ+mKwfKI#PPRCobMjK'
    '+<$($PDleJvHD$dGT;|tcb~}Vl_Tr;9ZUp=NHChf)9FAQuyeOcA9aR3-XitWc=#UJtXs8gP^0IQa4db}$%pHNS13$~9fTYf8q|p;'
    '%ejIIdC&`#2FKOkBJbhh^P_?6-CZ4nW#P&O&U;UV>;$D~6K6o+0RVx%{cfYQ;${4hoQk(6)}Kc1hfiyKecwL+<OhtMir8o(r!)Xn'
    'i9iXtm>Da@{oC2+lAnvMOjyFSkywTy6ZX)6Tfp(dyNB`1T>a~>cNgdW2!~hQuplVZ5dU1(!KGtbSc4CxB~WTHZ8nfkh}ea$x@I1V'
    'wK>PwUljK4AQyKBT)hg5nLxOQSi~yXvUSFZ**X|NGSCTPSs{prK?sM`!*pgKnk0aF#X4USCPZ-E#k>9PpO3g0xCrK|ez7yomLPWx'
    '{s9MobbzP@z{qym%)f!0G4Bm5(tXWzE%=w@9kM<i!rr%s-yRPR5Sl>A*$`@N0!136*obI0D|K)^5Ihq>M#Ns=JAK5mAXml6=e{WJ'
    'e%^iH{|{e3eY*dyoE2;(=9j_xXsGwZh8f^UbAqiZVbj6r*wqq1w*@Lbk{O<bMWUF0QQQX|**n7Glby6R<2GhB7HW;2wKZ!xKrl=i'
    '5?aytwioCJ##;l9*5i=yWqdtbN_$n-hxYKdM>B84-Vg(xOxLu_PeWYlt?`*@VDZM|J%Jsu!Fk{%rRjgeU>s|C_oAMB`uh2A?>_#='
    '0x!SYkNY^PA=H}aErGz9Y7Ajko|xU*_;E;1C<SKh(c9KT4yXuG5|qcf`-{@xo?kw<@m>AFpKIWc2kV+W6Mk|*MLmoxLteVWdvyg{'
    'c$^_B6M*3iJrE;i0@2(M^y&WL%iX8X_hUUg91Mpa9RGOS%8<<%5Iz)p0(g5Qqz@CEt5*E5r&v)<c&~^}3#N6LuI%3sp?iF*%=hoc'
    '`uMQ^`e7c|%$sA2g8D9WH}SoVb(pwuT|MAYyzAP87O-~R$q=~wY$Y{Y%uT{`Qz+W=`Q4Mnnn4H@i9$HxCBf?;IwnjO24rf<fNl6m'
    'B405uR;b@(M{q_s*mP5GZp!^~|8YHx_Wrx%h_-jzXR`!)+i{fQ9iTXnncWTUs;lkM9X}uZ^nQ1~0h+LC-fQf)O8)xg-Fw~YU%fZV'
    'f?r;lkA=}R%->d*M+2cxLf4kB4yge`isS1+$EusNc<{5EH`&&YVzpMF@ts}~+LtG|s^PS!j~4v677%?%WtwPY6r>#9&xAgIi-y(i'
    '*jnvycW@XCy4^bD$LE({^!##nJipvso?otrgDAyf<y?H1ZAO%7g1xt%G-AQURy=*_-X_S83x@3hennt2oaCm|zy1E?Y2i^&LPB;0'
    'ZkIehwRWNq45x;+ptM*Ea1EakohhtwIe;rf&?RVZZ@IoC=<DbE&+Rcl4Zql^5+`uYfapOj1gWVn>#+$F`Tis6s&UR0T3skNI30&R'
    'IcIY!<Odw=5-}Wl(qR&O3=|Xy>@uI+Ym2Fuj<N7{jD=^ha$*Rm2G1DylO?{ia|f&V{qDGjc$8&X3c%igY2gq6#ED^sr01%*y6<Sj'
    '#_i$e@oNRk1WOo&Lv$(TkzbJYTnMVv29cq9fi!%Gt{Fg|xYW2Ii{K*I#Ih?9`8GIPHw}b#Y_5tvp9=Y<z5jAQzJB=n{_CT_8fw(X'
    '5=x2d*?6Fy3A^g~rvpEy-W`^FkAm;^@p#w@0mN8v<mp`Cc=!49cUd6xZ9S~76L)p%TTut_5iBX;%cCATlj)`TvUZ3>NPt-a%*S3}'
    'Rpu30&$vU#$rZi-;1(TB1l5C%1jIMiAj$$t5+G!sK^bvDzqeLo_lE!P*JS;0&zv)8vp_s_YiR(3C9P^9A<Oc`d%<I|4jTf|q4}@|'
    'L2_hK46xOh^EGjQ$5H*dKop-IDIjRBI)k<ofeG&L1ieWP=XG;xwyD70C7R!Co<vAA1?&qX)b(P2MN@vz5*)DC9hW#YaRrZ<QsXBG'
    'eWP2VEzZ%%I|U3MYJ_#OEH_54M8%sI%s&gmwm<&59=?1Rn+yjhl3RB4&Y?JC`&DNq?m*LY0Xgwo$#KeQmeEVXAwqCA+$Ju2F81U7'
    'Ao6@A#y@)E)%OP18r%u^bz$Tg4jZ$<VN4rxC-zxACil+w;#5H)X;`=f;HSk`tl{Uk0?0W&FUA1J^+kt=JKxSN!W-7;aMzH^B!G&('
    '0u2Gyffb@P1dR5IyodJT)B7hREG1oyi-@tN5*EjV3vs2H3Mqn868Q99R|+n)ERcUA=tuBO0@=?ORp#^h^!4Ez?*kuxcP+4+%BQhA'
    'n;&{TDJKiW))U-ZAo;X?!eO)gD(pze&1`xGNLUo-1#!gj|M+&x^%%7Yk}cRYa+}i<OudE8?Etog!3N?_Pqqm^5Tx;_qmE+r`d<@%'
    '$oCfneQxhQ_WOT)XO&~Ysv3BQFcAITEmVU;o<I>?+QRQcrExMraC?_9*3@HJW?8Yn6l?hZ_v=XTOmwhu-CRLL;@AM4y|D745W<3j'
    'gNP76hbfb@DV(I<4!qR<B?({Jca4kUjY3OA!-)$D4G<prir}mfL~nrXr6@QP%s}te<D_uGkS|M&xFzH3=M9kiWr1n#euG9nyN?4p'
    '0Xwi??`t4(#v#NEJ_nU6>Vq*s8*>6Z<f|L*GJ&vd1auNh_ldwO(Rw@~-#Lgt6h_I1c4X<c9`-@hTAWR&pc<Ru9u{3EsDYwpt{~Qw'
    'W-ce9WV-L)w@2<6iH0bZQASDEEmvPGv>*X}V8K3tYdXj&>1GUL(q+mD8A^icd4D2GYIpbP>zq%Z_#Et=UY_7Lc_22ox^a6xmWsR2'
    'al=o$Zg4$Tko(pxD3)jPc0wyB;=aATeZ2qt;rm85y#S2V*R;A*BRmo^7R(zV6+i1ZK{#atA)+YJ!ZPtzv95xG)|>BD7m)u;e>cAN'
    '?=QlN?nJO^M-?ozt05T|1m3aTth{SCrV>PTD?hO~LR!Te2S70q*PTcjzpe4N^|{}-&yNqKIpSvrj1_*S+q*nF{9acZK6a%Z?4|1`'
    '08d`vRu4ZWvf!`)bok$OChqCOLae^-R*HZUAK!7JW<!q0u%TFX4{tYWBYYrt;Q85uQU?CRfs)smocT^yT~EBa$94l)rYkROtVGnR'
    '3#3-`wm(L$9e2d9gcOxjk+J9Q0bnbSO$^B=vew6OKc5B=VgZI6QRq}s<YWN$MV?(R7Z|b^!ig~9(RKX>Y74M6zp)jRCY*@c4T^?A'
    'KVCe6v<ZD}qypq7MCyUhF1;SCya<K(bu3(oaf2TZE65cgFnTQF<j%J6*L6=A{`}~76P_LRRiT(3LCUf*FuZIHDvzsh8(UiVq-xk+'
    '?=W$kCkAE+V*6&c#Z}a&-`c~v+h0E(0qfD8mjP^A+;&6vz!W(KB5^5O86T+<9Z`&kYU+Yk*$74_tfZeE;eTfmKevzb{=*|mM8$0a'
    'PnbNEYtTdOc^ln6&N<Nr-GUB1av88;nR<~xKlu{S`4+~Rp!+Y+-UljsF+3;jj7^cyhuba6>RAn@$<7Ads4WLZD~WYOHME$_mqSx-'
    '<_26v{Rm_>ln*b!r@>To7pQE8fL9u5NNb!v*4jEJXZcbAWST)Y0Vfu4n)OuF`}O$|W4D3^;%ZGD(^zym=!(JRY*W^2PUrJIexwP)'
    ')sgisFk;k&K3^f9%K3!z72*ZiKRnq**m*?Timh@zAd3YbDG6G*0wfHG1R2B^%9IlahrFqTfemSDx8Lu-i<ILS?fu<v?K4#A-S|#l'
    'fbk%sU|22(k6+3FCln$$5GNEq+EH-5bK}a)7_)G^Kp<JV={F<iiPXDIEQRO(?a>@yUD+dwpi1}{8(!9?5;Rlocxj9Onz1H5Dve4I'
    'wT4}>1+D6Fm@{+uvi|wy?(_OMpZQFP`ARMq;erx64h@4_c<gOvC0=j6y$gnPU^cqdIeuN2M!bUlaYG>Ih%CJke0tB(et5zvJUSBk'
    '(1W%+!d=UwL86E#Q4jdRD!X#)Et?Zgi=P>Yw|a`*x0iR3_QTa#C_OBYB0fyq85B9N33%{Wd<>DhL8pnK1`!r)5(u}-4yy8P)7|pV'
    'm#38<t|@9tMevb`Ss^W3w_~`a$PTtvvffcANKyfGHee)<v^i;!@>;N{GeP&?@2j@Qn>6SgJPFP47?u(%U`R>XuY7l43e;?w5t>g`'
    'jJo?|qpcYI&0FlNm?ue1&J`e&WktnQ_+&19W5tfx1)(j1_-*NV!8)Av2CGIT{lZ|@LP^gS^8<z8z#*^z;O>Z7nD1J4#TGkcI)i@o'
    '09Fiaq1N8)!0!{>L36rPvf(+s{IWhg&w~bQcvta=Y_d|gF%B>LbWwrX_#F!NEze%EAKtf${X{+eS<&aGAO605R952M|N0+))em(N'
    '4c7p6-<YB<!Jy+a5?4UJ12=#h*Bh^LCjgDZ)jgI0ZKnYSF?E-pKK$YCzx}`054Vnw*+Ar~lz<HX4v9||d<R<=f^&{~#P&q#20*k`'
    'j4Q&_Igc6oPapo}?%)6Y`r&gE0Gh}y1n_vO65@&jL`@``0QU;}ZV~VW5K|fx`!SbZH5RdJ{rKVUzTEwn|9Smz&mTr}nw$q_<uUBc'
    'F2ia-=rEl=b(e+4BSo+OrfiTQU`&_1eg3rPAK%~o_y2nRa5KSe*v<xf5(@A?gMF`-pJh{k1w?|VWISbg8|a$Xm3eaoH^5hjKYsY<'
    'uXq3V|GIuSD4U38;;hPaG*CY94hGAiHD#D`pj%4|HEz$Y0ggN#k@yYj@1gzp;s5)$yZ`*(uOE)Fn58^l8ItZlt8^ULiOa1vyG@MT'
    ';6f8}wYc#T1qy=F-GB-+-1zYS`Tsm>3!n&o4GU>i-?-~8aw==nI?&GQgIxe+0BZ6~S?Os+32&#q2a)v2hhsm#er_Lr8AgeR`{B3a'
    'npX%H!^EqE#h(uN6vuhNw(|CCInX736@NKcBHtZ2)wg$esghlN@#Ft~e0)}VdX&wqrnHj<4D605gmUZU$O6Cy5D?Rc=M@FOqB{WS'
    'PAl$N7RBSoTYJ~v-`{_@`>;Mk!LLcnm-V32p3O+OFcl=!5KLU?Bg#V9*T(TnM0LQJw`^6Ko%Jl=O9HIbKOpei3WHas2pAmG0v&_S'
    'YsB|2poQs5=YoJ&zSF29@SAFb&Gw{s7jJqi{-!{85%_ES`tb0seY`W5<$-zdqww$o367IZvY4`qqvy0{srZL9YqO!3B{IH%sOSwK'
    'f;hfie5<r?Yr}q*^4am+ur$LVRzl&Nx`0wjoP4cdZNym-x2*O24%{t>7;gS7o$0$Y|D!Z}Jvx8^%CKU>U0Fjcv{3A##7(FXL7uMw'
    '=VxgnU=2Ozfn^Z(VSMng@>Xe=b2A%R8`z%1g3e=`MhW_+jSU=e@P5oJCZ%joyg>3p^&bNJ8$-LTDB;_K%^m-F5Xo-~16fHenpZ*}'
    'E_drzGxKx9_6lX~D;saHRRD#98YpoT@qU8ae6IUVqQ0%FL*3^l#J8#UIs=0R9Fz*@t`pCSTgws7P!?RJSoqcgdg+lLWbJagDJtIW'
    'f426xn64LKCu1U11CiG^_M1^=UmbtL!vW5C{i4jISU0!`iPpnt?U{scYrqvH<#7?E>$k+Y3=FOWb*N%gS(B*=T(tCblyTA>2ASBk'
    'WKh0-TMyFRhkMaC)Zy#r%Tl&yi-3jV!WE8%XDbf!v+z*F0QgU>INq)5FJ)xK9#^t$TcR5gIlMv8#g4q5u$9&Ug3p!s0D?ObE{F|#'
    'Lba5AZ&+XJVDi1a2gK%KGC^thaZ^x60Y3eH*X}Q?QEs6^p%6DD+xd!TVoh<{#n%SYS1_NAFhj%dN;r4uRB?J^V1L^yLcXm;1!}A1'
    '1hh6z*H;z`Md$Ji0WRlf3%;e@YgcB;&U$Ml-mcg^fxzC3M)Iyl`cM1)yC1Jj&jNg;0W0b~T({Y{0!)>U>^+5^^zH(HVwq9*-54^U'
    '0G(HD)Efj{&PF$I@{*YKk!;NdI^7RP)J`vw$#knr3DmGz0^f0MoHwz3&YW)Qv%8!3`?8QD^OOx+TdDkfR6f#}mulC7L%mhZNMs-c'
    '7k+vqtddogCK`c-z5My#zCVDl9WFAh7DDl92W4*K0hZF~3lt3KL5N^0%P1lx!&Fn|SV3{)*5~`XFRgvO`}BEzx~yX9g%pZW^`2o-'
    'q`>mcSWb5|C>ro-E6+X=0|vq&uCP6T8irifZRL6@<=a|thRKG((xU~Xs-&YpM_ngi#}t5T1#bmht)LX#*HopB4_>Urzi{Y5QtsA2'
    'Kixlkg=&3UPMAqVKMA}lXEo;p0l{K=Ro*#f(<8tza~7RX9%y;+TbnCmZRrhyzOBePfRpZ(eTDAW*%O)@Ipw-=R=R*|sDP{xJKQkX'
    'R!r(BzYoyzebdFnJEG$ytj+s>d_1m;t}6pMyW!DbXT2^B;nj<%46emh9`%tv5usT<5Mot5JQJ!3t2=cC+dm-i+X|Bjn^UsDL3Yv>'
    '1s4^P6ry#U5wxp}=hk(kEjXa@Qt_&4l)z%~mS#+I_wcvhkJsOe4e7zRL+>HL9-Yvg40^?a=9HoUT9q0AxKdz%@}b%mt?XUdiMF20'
    'xcC8%+Q7!D17WqML<$t!LQtK+WMns4BKww&7(WPXp?P+p8!;Pu3t3Hj&8fHm9Kz)R`-&&#bKWQ%%fW3f;9Fk4DcJ!b@}4;HPB@B6'
    'r|=-X(blPmi#JeBYa*WMNjw8bVPB=h&;c^Ws(5H9y-FB);RZsB2@6^Q6Wyxgy(Pk3MBIJ-q>QQWxW;t@m=3T3po`0BoJ*n~zPpr%'
    'o}X+RC3fhmi54Q7RSHlt&}KLj+8c#k@S3g%QSFFR$Z1DDeGfqq2XqI_2l#!*@o*q{6xFPxGDFmRSOuis+&uq1dPWrygK6<=xZ$WA'
    'yP64XeF(GfZGp2k<0<&=wivjnjahm01z_k>dhz2gm?p^L!RBy$-|&&T3fp|)tdy&;Rr^tOOE&s(#p{*V3e<%DgBR>Avz*~>f?q#>'
    'Uf+s`o}3HJP<Ims*J)truya0kO4k4!_y%OD!n%nk%&lTFm?StYDs%Q(_VJmp3&VqO;Y4S4=yhm2>0&ytQ%aQgRZk)KKP(;WOkj(<'
    'B;CgaJICOFZVPL7zpjr;R$GUaKK%CA@eO|Lp^LDJ<8Kox4Brj|<+dDM*E3`<aW>1!VM7yKV?_BNLQxMx{uZ5_zukRdUG3e+E5L;_'
    'HPM`+$5h`NK~Efe0x^PeS~e$FD(WWv_8vg$_0SL?;SgAJF5?pD%$T}F((?=4-p0}=2UTV2Dq9OFr6cPh<1}&0MfYdWl9)1wROW3N'
    '-x9Si0UJDq^V+HsHn2lBX{*F#fdO_`y<#LJZKVttC-(`Gw^DJIqm+^}2^UubJPFCn=BMgFJP{=V%!-Ags%V&)rzh0eb&Y_+ZJV-L'
    'ut&&F_ctBpe)q9mFD8_B2?(xDm~SK(VHEf@qub@xJ;)1aJeA*IiX&u3Wi5soQ7i1Wj~74wLX@`BtCIzOrRB4d&@3=72RvqE$NgBw'
    'ptB1!1{<pz{22?@Sh^mcZ~3lYFBb!M?fv_^hx_;M=4C0~Z+2FN8*yyNR_`&|&dK7TyvQ_oXYwTgX0{0_<HsC62VbNM&Hgr77js+%'
    'uBY6CS@pDq!&E;8knOTli&ajk)$48(O)t?VqQq2H0vO)DD(lPV_VMA8MRZ|N>$vgK<rd!>5V&$w_4ZJCag(Dj3vW;y2aldtaCBF`'
    'r<qV*e>3E~DDm4G2hI!cVwag!SyHX*MN`aK9WYdw;DQq8GaPNn%YpF$(;HLvswz2^`1ylKHG${4KIjJy(qS?F$Pfm_sgs9_X>l`X'
    'en=dWtq#SHwRAU#@~Xf_XGXcH=QpKYV2J7*z!!u`Q$g@aZ$zA6nr7=oj2+bba8T6|5Y_8a9>?6BYWLRKXVQ9mcsH*6DrY{fLu`pZ'
    '_P7a#(t@X&-`?N>`?Mvin026|s>^H2dgp9A!MD9Z&bRdl1~xB3llQ`;BZ~<mCa{gJ1V9S7gud`<h6C+xvS={sAn-cnU7X38O-&N!'
    'w;n7P=Z%9%wWw>rQkG|eM$XJfPF+)QG%G7hYr%MRS0!81(e^U%2L5K~yDjc=T6L(01)QM>R?ntHV;FnTG1lcW?39qDv`(oAMnW7?'
    '#RonO$nDO=5%~0)2bZ||k%__?Fe)bi5dm!Bu{zeJ^sLtPCbdy`NDaXVua3VRdOpZm9bC5eZ<ThTE_hj^wBK1()Q1=#9Wg>zwm9ZE'
    '3vdB&?;_q3l_|@Ck=pF)Ffn63lXk6Oy106AQL7r7-V{MqO5JixP8D`UUT{Ti3s4kSMUiltc_s(XK`)ie^%hAN7zX7sHaqCh2soeY'
    'RD?=sf<Z<Tq8kxVPY~8+CqZRQJ+X;IccYBN8<L(ENq&PV1PHJ(z>82W4~!WRxY;<9bfwV!4s8Z-YqmJ7N&Kq95C&Yh+w&$fPsKc}'
    'FJC`hMBvp_SqiICWjnjB^pwWwfU@!?9J>-55o>^cpsX`2e#9;oWzOt;OT;I0#okYdf1c<Z7Ox^wr+yBjdOT@KZYHZ|dvH0M2+M`l'
    '>EgRvBFfl35iNavgPaSE?1Xq?gK1qo6b4=nMk=%idr8>W#_1LT6+k5j8T)E5D3lKaNbwfM`&JEjy*N+ka0Sf=rJN_#uRvPb#(&4r'
    'd$r2CCN9pM_tAvL5;*XyMK?F&<~7gd^S^z6g*<0;J%(p3dU=_Uph<*RjT{1}lK#*x98`mIjft{*6<+IdCfxe`zu*7!_iM{4Rr7#v'
    '@hPhPSGLvYZOZ&JCZS7gmmofMhuW7*guXDBuC1nSZd`le<L~Z2wg2zy`d$x$DV7cscBuaZud8}-vxYR;u8@<e76xb4VK5_F!#d%Q'
    'iJ!{A=9i>A4dK_iWnWVP+|63}mjM!CD1*?~am~<`LaZiUG$<p2Evv?pdq@g#9FqQYL70WA2ES(;C4f)HXJlQyH3M;XUQwrc6_=~H'
    '2hD0Ac;Fu-qB!r>K?h!s+jdWn;_~kw8hjNomz~T8yeA8^DZu7nb84zGQ-X8_1ZCBc2SUJ+O*pz(o?XxH!j29n|7MlU|LOkW-Iwbv'
    '4ku`I#7^&qDd38-Dyt5;%3Wa{xe<H_DZn{c10aE@Vw9rIdd#VaOJMG4ZavDeTUhi~H-J9j!0X5<fF3lJeodk89`mqA?l$XSUsFq`'
    'tGK@CxRI5^iL(gUSi>(V)qLod2LGqm05u>PKqqG-0J)T{q_?35?BJih72J?<xff7#2(VZO(GKGLg86S{&iPXH>N_$7EAXEwgt;Sz'
    'QF3O#hSIU@vm3UbWn7=#VjO5E@V(BB^HkCbtSRdTG>ol|baL>jWSotq@LL4ph?Z3dYm8TfTnN=lO6fK3p!~*l4;8aB2Z!kopSCO5'
    'x>GV`Vb#j7u}5NR#nAv0Pla5MB#RYTZ<ST38d+gHV}l_yRa{aWXkD@(BlY7Vkgux8U-lQv$Hw<7LN2h#W&SvSE;OU!9ZW9Zw98Q8'
    'Q_Q;Z#I#X_sAd7!+}5jMQ3;=E&nfiJLfmB~Be&H#>@bEhq&VR^J%OPModdQ&`%3rMod#;*IT?{r-MDf--j`Q|T&Tt{rr>g7RYn#*'
    '*onds%RxM?5n_>V74Z_zq$SR+cpynyLkz*5pNYSki8=D5MaNylL~z1e)y7b#EG^S(u3CU3J$?pM%S5jn^H9MK==Li@E&<9$*Yrv-'
    'g7R|Ma&Ry9;KZv##0eI>qLPc?F1r#xK(wVaC4JWP%65h88x)bWOqDO=yCHo8nMN;cY);XAB%l{uAHkLY*aAWY{^+*fs}<UsuL!wV'
    'JlYDf6$V%<>6O%YV3m!yasq8q4l*0KlrdFR(Ajkzq0TZ+Ia=o{iH~<^s@f}NMt`($43NwSo+%9#R_Kjp5s^Bs48=BB>?|By(EV{='
    ')x09)vJ&EJ0SX=ui?|hB8StSQ<=m=G(d>4p$(c?d;VVOg7H++|w}dU`Qz7Z<yF8&JvH5y)Dyxg0SJ*ci6x6iB`+!ct$UVTprHM~z'
    'dFe`RRunJ2BINp-t{j)Xhwe5#r#mXFq-W@dJ4(Q_s06>DwrvJp0qBl;$cL8$xX#^5enRe!;*7{QuRz08{Z>1SWRx9ME&Oy6osBw;'
    'h<GJyu%@m~G622yijZs0L%_TGwA4PvWO!52_OoZ3E%Cy%Wqaa1r=iNlq9-pD9NjBphtq?1k8grdZ>;xt*M`mExRw%@7ZC>FqwM*B'
    'xd-+49A4L{wWXzwK^-XbjL6-P@ogQ@ZvHr|6MBJXN&{}=g6@7owcD*!Ih>GG$WG`y)EkTsK#4dOlTMw=)p02~oU=4}Ddip}59(d&'
    '($S{c&qab)*24@QlQ60NBGwdCp$QlJYR_d{F0>p`Hk67Bu0Vo1dR04!7$76o@TK`_+)kGZP&l(K>s}cR02TxJ&SYHOmKl0Aas*NA'
    'ilEDn76(GKSF@b0R&^Npa(?Ydv{l>Fwi|{xmJ6+na~a>((GE^f@N;eu5%DbSM;Z~hR@IEedg88aJ0))cUM5dHY<jtuM94Wc!>iLu'
    's%eXU7Su|uP2qbg_B4k-lu1O@`sJ`&?|g><%xB>a#!yFbrJc*T;KrsrJ(jOajJQe$t+cJ8-AZ6pJBz93;o#a(Dr;7Uj(T*rj0YP%'
    'lW}!fN#)>Lyc&XT-D8Ih2hqVyRLc1j@#vhn6f9zm18$;@bz>46b~~4Gv2%F{Y(lLaOP<}ASk+F1uw534iU;-vb5u5pRc2o<)_`2L'
    'dVxG|BKVVxtHXjBvlXu6K=dMBRfHw_6y{KyDgK5z0Ap11xCM=%(Y^31DP|?yoy)kKhZU^pAng^v*ShSAfa~oz2*?1ovj*V{qpy0J'
    'QAPuc2IpCzxr?Wsw_Mi&1W_66n#2PSK}c%RgKek_lyBcshB3pb7kZ_FcHnPuaFwoNl5{TPvJNE_KnMI*x8kNOH>Hum8(=4*zDGHc'
    'dfwtKafWU7WjsrVMuIO-p+mi1Rl|{r;1rmrPqb2L7UIf!7>D{dP{|f!)9qtbv!kJ=5{*FRi#bPg^)c5c0Uy2ID2-X!Y)B@i2U5jX'
    'sqQrn=%(3xbkg(TNb158AlU^@Ja75k>&x5q8c_6NE<UN-dbB~Uz^jbwg^*`_B(O^jcy>(EZoMjWJ@aAkx>DX}5-w+<+_*{fjtQle'
    '8wH{+b=~c1kqwIT0q^2^*82=c0)7d=1$0=i*Jl#!V+e%33|X5|S5VMx+3NqM#^0$Hb(`qKKESAfPvH!@V($<+L2%2bA}*NZDDLrn'
    '47Ec8_$aBcm;m-s(La=AD%-~wwVnj`E<MI!5iA-km*<HH_ZR?``8|ozY;B6oCnx}ZU9CrzjE+u$cwwPYO-S_;?s1vj6<wFul21ik'
    'o(eM#cPOpSVN~kWI&|qQ*wmEc>-%x55Td%Fbs-^7AcxUay^7zw3b=~6hVh7V(qogFH%*80i1;3SVJMjNQgK__9Kiw_sRHGA(%ZVP'
    'u|9hc@~Mam8o@8=8V3B*jQW<U8=wJy7RWmo3(V1)erjNA(dvy2dWH80M+?0(tGEX7YL$XsA{ZcoV%fC<q9#nIsamX~6|7x)KupR~'
    'OL!?rmghSqJaJ5?A}(yu=rTr+*QTk^aUUs`3oKhzgX-0mKB&`aLy<a8w(40ZW>wLHlWrdOkI&D0vJAovY(I|(p8|_uB1u&(aq7xf'
    '{b+QXxLI3fZ&Xt^hftSlz0YO*{hH0?cXPEJNi@n8EXAz`Juky1T!$LcsJaa=p!=(>ro?7?-(~}0h1)WoK0nt(^JDE^{nO3z@FxDC'
    'Q(jo|U^tF}9fI^LW1tJr;Xkk*xHKO3RKoS4(>T-*;<|w&5IYMUi)yf~XU?knQ$fPiAcU)^Cwj9HUryM{xT{+}>gn?{_`89|y}Bq8'
    'MRRLd+rlwqJ=VGyIUZEGs%K4827t<pk{qrR6pb%_{N)5zv%2;_T6+%R7;(uOm9Lcm8W2H~h$Ks=QdQ#@N6wszZryLAkCP`%T!B}s'
    '4)wftx^*2+*ZROUFngbPBPcRP=aO3V;xK_Igo>gYa{$(StF+4_3ofzxY=^q)z(T+cVP(q?^+JlD4?`Z!>t;&{f~uV;Ro!EzIt`t!'
    '?dcO{?zW(wYjie`Kc1lpV^{T+Xh7bhq7%NL!WM9+pq#qc!slz@&)tlJH=i(rO0ISWdYG=ZA{(y&Lu%M4=!ZC^>k)NXnEDn@8P@O!'
    'U7=tAIgC>|obUZd{`uj$D1wtUW2(Jxu0bV{s&r3B5A*>8O+a{dO?$NfNN#&2taf;OUSOd&3cK71nG_*U6^2}Jki?5@wU(UKroICy'
    'V0GxpaU(!1m=zMVOKvRJm|9PTJ$aP{=PKd~qdN9PjUUaoJEp7}w5aaWN>>89qTl>iln+2DQIN81dtJ`A_23XW4;;ft#>1K?0cc>3'
    'xsHn6dfjhAf{n*5_eK2AAi<Dv8?lDxoiT39xsm?hRrUcx>+IYzDz~y~^`T4%OR#WY*d9F2NV%)6PgT6?c7G-_SiJvZGB22*I)aQY'
    'PSm&7CEWoV`B(Yt4Z2)abuC_rE9Iz0#8T2$sn>33@R`hK8H;IzJsK`TO&*LWW9VzE4i)xf*}W)02Dl~Os_oTKOIX<Eh=Xmt>BfH)'
    'bYaK%&#ez!4gZghQ~+TcXs3}*%n}RIW6<aq)iJ0CH0YqI{7ff)Cg@h0z#7dfHM;8&VzY5jF2Y?E=CMgr4$yDK3p6uED<S*?rB&wC'
    'vr5ZzahIob4$9`EE{Kg%o}sdc)N@*Qj7<sCShMd|)eROp0(;K5Le<M_pvRfGV@Axobv-fMpfnth4!wc0#nhDh*=On+s-uE*xSz`7'
    '8vp=8J8}~hf{KI`dwP?o3roNiPE~EH1wxEo%?J&g#;K>OYgIkNa1wyyn!)Y@7tO0hVpPYPn`r1KQMU}vHa>>fJd}B3@a#1TCF1dm'
    'oU)qy=dO!-qIChy&XDlg<GAlg-TYy(mxM9YTMb%mRUh;XtW#%IICbH#QFc|jvj;TDpTN4n#!(Yvl!?KfoSvcjNk0Oo)b$q79R$vA'
    'aG9F_b!cd`9@mUoPSvDu4foipWmO8_R0gyPB-g8AE}kFYq`43dsHca8HCI<R@>uo;f`LpL%$rYF6C8_@M$^DOd?B>>d1sKDVxECP'
    '>!!MO5p(-dTT^h#P`m8mq(l|sGODT8rpkjY(6gm?uhk2oa5(GVDC}Zu1Bj79geiN^2PfyD+62r1FUaEc8F$b37^0pa?A0qC84exP'
    '=l(R-JT{HJ9u`g`9^J;2Nnm=_qyr#mj7d4@wHB7CXWt@Qks5lmLW3v_^u*S;$+}eD#H^=8STo^93F3<#R93YKNA4p3Fc<<`moPvd'
    'ReGPQtiGx1iaVF}@%r)%hlr45dl*!<N2mD2r&pElt70PUSY&ch$tz05RsMJIL-RQ7^skD!q<buVKjjGnpkbRu2>?~|j5N~4?+Fhz'
    'NVj18?9}g@hzhiZfK**po{9O?K7V;P-aooTU5w~aLg5gvryLSoc!wniV7!zgso<;Nkh|Twhw8oQkv|TNW2tYGb-De~#G=$OAAr1J'
    '@71ZpfTFQ6^#_4P3nhpj;;uBniYTy@LDd^zf4)i94W}3aDy6$zr-Tw00;@Xil$Tm3W<r_DBsN-!$84G!gqiSF1JH5V=>NFfi&jD*'
    ')p$fB6I98l4oynL1k9-wLQ<7r1wOQ7Ss?KU!-oqf(J!x=Pcitz-+q4z_y0^R@+j)hM<YL{^??qWYq+YaUIKchgWnS$@nNobmlOh^'
    '8o0GJR#l5_SZ6#M@%n^;zE#>aQ3)7`hfpU3KF|Yyw%7CJthz@q5#|xp?PRHInL?Uw9H0axrD4w5%)_U5{KEQueXn?lD(7!mS$1o)'
    'e~{W_wki<R0891w*0cJe9=;r4E6MP?3Gk|hGj9}jLB;r2-Py*aPQhu1uy-{c`ll9YdUGO_PF-P?fhlI?%Y~qBVix4pPVvYOcQk4N'
    'yiL<pF3iDX)tzYSkQha^s`ZSpLoa}yWMVd{J;2<~oASI0@<mA()S-KZt#=OvPU45vQ}*yBD!X~^sp=M-r-j~Uc)hxHThX@Gx|$2V'
    'LDI8SbzD_arhBhmDT-N5&^t!17HR6y2Z?bzL!Q-GD~N-HpmhJ1D&FTDems|Vxd##M<Wn>{q5ywObGGlX>>%VcjV^RV?&!99pdd<K'
    'PMJN^aI~hmb9v9q4AKmi!AqbHDv`}qy&o|$_R)5rF#E>e{B#-^bpq}g4Ny!`Es@=Nlc?(ri1M>yJ{s+XRQS?04bE?wm5+q8tUeU_'
    '4@k&{2D)K~=AWd+W}dpLXTYj0>}YKuKi-ZX_^Nl9Zs6IfI|M2y`(=5L&S)BmQfuLYMNJO-d1QNvskT>#UMLEG^^}NWRRwlIjUf4X'
    'yU}>721+P-)I0VbBE<O6k%-o(y!v@6FyE@<uD2^HR*V{jp&H;kY*wk4-oT?0>8g4`rk*u4$MkNt@<m+=Z;^EI@gX-=X08nK^Lp#;'
    'x-v40lS2jgl}Mn%L$H51<fh>qGp(YD2F=D>HvBX{7qHYdL)5DI2YAV9vWDM_s0U5i$!!(5s8g9z(j1{HER9WQb5z^oEr9bZpH=Bo'
    'YW$o=SGIJ(=QWMVOhuh~@YLds0BR2&jQj@GXoaNoD34pb2{fMNvno$(5zm6zWm$F`s8TqF#Fu4d7^^)7vE~4bQh(_|P*1BxWhAy@'
    'U;O-M7eg8&Rn$sMjhe81f7BL)tQx_gr(_Ja4NX%7C}K%^D*#>KqtqI=?boDSuI<5E?ueY=+R*V}stonJi2((WX4kE)K{UUl@mcBv'
    'mIwQZlHX2FxkwpbzubSlVlV=m7^l~)N}Tc5TpVW?5JArn#%=IQi(6TB6${Z}JMR?s;R@|0^LQ@i5)vRXJ{O2m!=I@SuX3Tf66E1f'
    'p2(Sxu5CzGN0^c3k<r&RXl=t>Ul#MjRYEg)IrS!0WK%CuJiO`~#09eIJHg`W2aZ{)l5r%sO)_c_Z^279k;0FHE}$7TJ-TdnQ?bTX'
    'd+60^CMLr2Hcm{`setjQf2q#nuJL=oG=h2yFAI7a+AxhJ=iai$d~I=OP)=GQs!NYk3YNN6!y0j@t>NKPg*F6CCs|<ZYf>(kBI;#U'
    ')G*5s6K$Cmrmi^*xJ&f`SKqEs_o~^0Ww(hM{?xpk&Qy&AeOby=$FIiqNv$sZR50G^_^NTX2#2#1#PBXg#Q?P)*<Wcbjid@uV^T)w'
    'uSvPM{S}k~4pC7_35*qoiP{mNiTNer($v6f6OdhpRsi{3A#eh6l!udFUW*>hFMe|Rp^mHa#KUN$NmG9(5E;8j1Rj1)|82%7DhBco'
    'Y5}sAdKxQL=GNIi%bU*tFC_x0<l18>;tN2`rasU~sUHoyH|oUDG#j-W`JI`;ivc&EUikPQ^5zw8vxa@CFwL%2blN^BYRQYLZKzu3'
    'v%I0fa@cMYi`S58U!D(idnV;Z-n=@HSG5|27l0d%(WK33Ms=_~&qB1F05eF4ny4AIXjHMaF?%-6kor-S`6>5&=-wshy}AK}{Lv;~'
    '<C1Hg8snoudF$v#Cw8JfmumlDHJ3Gw7-u<?aDhk3t6RlxFjPJVY7x~~)oegTiDu!xOaVAIuhem+7pQxehB6ZbMmv-6n0e0rkD>|y'
    ')#vYA3gH62<5jCNH250|q$$c86lRoMgkxx+r=%-$8&5@CB4pQaY-7hR{6Um<5?WHhTdW+_x@D_N6M$((&56P5JN1oGd<F~r?6u5|'
    'ymM@L#|#DQA%ei5f(AFMR}^B0viDR3J@vlIN=gK7u_in601A!%^TVCFk#-KOfxLuWQ+Cj$6})aK)R)s^#%rhrK|hcSU{==#Q+?l*'
    '_)$=+QqN>uk3&zI#muFYd*u#5-}O3cTvRHxwHUp&1-hv+rjmL)+ICG)>%=9Ga~YS5Kixz_BnzZy;Pf=1aS2mZo8Wz2wWbTME1cC~'
    'Q9e5`xGqw)qJ6qokBOUVz?gw_nkurLkk2w6#L(5y(<r~#X*3+ilT`o$Z7P%VYPioae|~Z_H&V{k`GrGu&=X0r&~-mG`&yUS8i5{y'
    '`ugA~n>Ah8z%>g5ia?ZB%$ba9#z)6-ccD0dd@1ai8UVxh(J5KX5ZeU1$fo@uPL2@u{(-f2z`iTzGA{N3M$lB3QH&1-o&cOzmn(I5'
    'vK8W2iu&zoo?liYVim5`RVQeuY8t_rjBCyZq0@$?H`5x~-;Nfk><KNifT1d1i9ZfrubC=h@nCVMe12;Vu6!=z+QJ1Vs>~7t`S$!{'
    'D~Bj)CT_vi!a63OdX&ZyjX}r+N@b5vv{yG)XELsdn`01hj#-6Dmd~XxH3QI8N4!X_RWEl**ciYc6W?AVULfVi@9;jK%ebIm<z72o'
    '>8e6p^O`hXX#(mRG6+mgdG4`G!*C-FXqv6w%d2Z<_*30|%r)Ofp+5X9s@d|<VpP2$yIEr#k6#fB4Ovs&G(MNuQBuJx5(EhLugJJu'
    '83eU&O^%SkA6FU%o-c^BVQ^i25QRv5RjY$AG$5)gnP})*B-fww+0BhibM;46mIV78_%KVDEw1bR3RCQwhNeP#i3@ywREum4vTuYH'
    'aqW37<C;ulS#s9kY&ASAxLwXSC5@bI8jP_jAhiXEbWqNRbLup%o}o3WG1iF;_qYtX^bu9!)74<twFGw7J)`CfLLeZFHVr^;Xi9?('
    '>YZpp*NhBT#;J(M6auwMR{IMzvg<+_m>V8Lso}VFwZcpu7^-nNCP56^(S%qj%CVhUxqocdG;&t!hoepc&Qy=)?!^iQx`q<3p^+p`'
    'lMaJ{w}qHmRcrxX4|gi!;&UNJo;aaWG&R26TlW?kazDn*%PWy}nz=bHgJXfKYaH;Ln&YYt&L<+St_sh*hDpcFFbGu>5Ly1%_@Fk|'
    '6Mtfau9S!GG(~RdnY1ONo}aOuinydiX*h%Oa3j$nX!_wuVjvtS7BJL|NeDq@J~pcLMI9<4Hk$FP48ju;S4Y*hX1Yf8*auQttMoTL'
    'f9;?Nh84G>J6E;K1z%-Ah_ZT{`mbxOxXGs?uE}Z21c{o8+4!WQg55&Ub&UY7L8}}FE|8!L@@X2;4gxzSm+9i%oyEWOn2$otEDrAs'
    'GR`?2_f6QsvQ3ri$|%+tqpplvJvS-UF8G>J0XwJZry?#7BU`|1ujza;t1os5wi!THngn7D)EPdObp>s*Cs53-9-AB!!fihDAdlJQ'
    'QAISED#XPqrDPQ~sOBqs<?iU5R7V)Ao`iyMm%Nx)aum!nr&AG^y9TQ%Ho7~9)g8?-f#E^<T|<{vglUyzJLsTS4s~5=2~U@i#Hs6<'
    'U0id^t<?k!HAmQL#maV-s&)!sGK`T}sF4dqa|7RSPWPy~?6P$xb%-?PRK#T!9s^T@W{^$9dFsBGod9+!`?nVLpwPr2I6M4>c$W&k'
    'EP~~ZkuYaSy?$?_E7Z)QyHA=`)x-#JKBWNxhFWD3(*bF5bqfJh+v29`W_4+pKVO%bpA*Z8YeX}-x}iJdUQ^E-6;^iQa7t4l{wUAE'
    '*$uX>>L#5vf(1AXPrKm;f6OZ%*6Bq98YlPXH9uf^_4ij%C17Fbt<(akR1B*5szP#0SBnu{I}>n4Qf+mGX_u!E6*!^Ipjkr7isEyj'
    '`QU21k9#+D0)ZP(HAb6+$Xhn?V^%pp0#9Jo9=${4G`dwY|I`6-tMkk3%9X48Xeo^YCL%!!1>ZEmVU;rpmv}cifKb!oP%2MU0As4^'
    'H1y-aKw}}X-<?NAr#<*%_^|?5ZPqAdcv-?@&?aW?q3dt>wgzjvT+|p&6I+*P%QU*VoB(M@I8k~uQ81_$Rn@%ZOv3e!3fMA~U;_V^'
    'w+6FJT!*@Pd(=xkNLQ>~4NDgh#6`TT!q$pS&)Y9c@Q(#NI!0xy!~iB(29VT=Lrar~gVS`AW%O2%R{=d#&AUgHrp6S59Qv7r%iUDx'
    'Git1ygt94|N^sc@jXd8VJ~)jWsca$1+xCdJT7m_$R%34w{2%kl^O8~V)uXatgHh4T&Ya1d)osnFMS+7^s^G*s<q7V`AaIlj>h)B@'
    'wK?{($27o542mkz0#}&Uj^xCKksZXb2Gw_j;|X!YWe5=_C3B;C;E9C0cDY{*aI2*msZ%%4PIzc*9$AtJ#!*!3i+b**oPnTA*R6JX'
    'yQ{|3&0DMZF=IVe%_2~%;jD?51*C7kJv?kZo)uL@gEnSc{wR=5>OR|by{i5lYn_U?q>FF_QAtSZoTbtrZ8~9Qsbg8}gu10@tAV8&'
    '8W(L;H4-$$0Q>z;H$^<XP>o#;kW_$Vy;>B2ik9lZunobeLGY0uPZ*{8UCj|x+OC&Me$sb$BH((~B`u%CRbW5CPgH6<#RH=`VY=EO'
    'G$~OVnk0P2_rZVFtnU3_ou36f%~s!_D|TDMIgduXt!tPpoG2}$(Q~un)-iC)p{|X4!3E@~gnu<j-0r1MzdWqZs26p%lhBn_Yq>oH'
    'aTgHk(2Pv5j!N!x2LBBf1=~OYMo!=**Khc|lU(%zP~!92N{S4bFcg&MuL^{u(Ijf*4v8$vSmsystfLTKg4*#V=iVsn!odo7w(4oQ'
    'q5tsG=vqbiF)GhSn8!}Km{j62c*c(7DR+WUW5qS*6JbwNSAvG+iF9|h(kD6vbrE03<OH{>y1*S>-^6QH7z9F<nU@3?(*)k}s+dcB'
    'jnF$=)StiBt(IOm+!zUa&}8Bve2?J5QQ6Uy7ls?r$Od(X-K(v$bNVSy-Q#&J|G@G}s><(!QBF16G!3#AG}urtL>()F)y9N#$4i27'
    'QS0z?!jU;iQwJ!6W;Mc47unShk#WsR?<}mU@r*T&K&aC8po$9h$uL<R;Trt<RMykSNY(nO@I|JW)o2PWYZ{<RurwMkE(6kxg$Yjx'
    'oKjD!UvWrN$?>aVt|?-=Vr!^`hOiW?NhM{e1DdAXFKKm<+wbZDezcHjNu3hbC}8-uUlVg9J8T8)QmJmWX{1HQ3AL(T%}FdlC5TQ1'
    'v?UE+h@LpAzS8!y94$_B{X-%z{%%_Wo7wW9Fovi%sQ`GC^K294ToSXRVLOYlzzRHW)B7R-oTEJv`KM;6n}1P)x;f2HCqAeJgk~A$'
    '8OqcZ$6D&U?G&zKZn-K60&s6tGt`SZd^PF6I>z|7O1tnQrz|iHSkUyu(F$s=9?k-oc-5hPQ&APOz${g&2XFWudc^S^wT&mzet1&W'
    '4Y_uO$lq)RtsjS>S{9slB%I(LfWI7~+LTB9#8jguFDw?jX>XBq@r4Np9X0kMCx{(<K;wKZzR;?J9IgR~V{}gi_*EZ?GBIH_IoG(3'
    'x!)k^hM}oFSF^iKOc*x;DOB8W7%sx21|(+nVgOg^D@>Kfc^5PeOV=<a{86bFkD}{Z5Me&5b{FK&EO(5!!?o+~1oq?A5KF1u;8<c1'
    'Sc9V@o;JVF*7`IvJ>oUg9|#&dRNV|_(j(4P$8=q_R`3}PMKCp2y}b<e%I&%`otWWkQZ5m>9UZ=^-q1j<s`67?bj=+Z6ZooL8AG!&'
    '!ZEmT>Si?9rIydVwe4P!@(lD5>uXg1;(iqHiL9@r1B~jntMn<St~<(*#MAE$K}jp6wD5_eE&DcE7v6}K0`*lq0HoV211!syb(OIj'
    '^^-^zXVtJmy>e=9f*Q}bG4#G)`-!X{v(nYr314q*0PAU73oyI*q6QyT_|0g%wZ<@aqP{vP6+L0>q>&LC6#OPp*Fy0u?bSFN<#J<d'
    'Dk2n+T}7?lvBA0ZNNrVEtxHa0)sYp#bOg^+QI9$4!(l>Q%@P`{NAS|)Gw^s#O}QZzuqdO%tbJU@s_!#?3d_?J%bSn6w^GuZ8a6Ev'
    'dBNjYaBtbwtr`JBRt1T2UOmGu2BxM|0$l?nko)M-oNB|58R^0oI5=I#5jl2)oI1~Xut;16;jERQ3PtPMB1{XpnLzMidzB2F-z4hq'
    '*Q6v}sIH-mhOIG!OBhXUlY3FIxCVges;i8q=4?lISE$55S@F2xEuwBX!kLYl;_8jZLl_nT%0V|KdZ#Xn8i171#^zLej)kcahW><G'
    'x|+@V4~xCLIjVC%<W<#%QFG+fBWrI1Lr}|}ikncOmDM~U1HjT%rpCyq9w69TZ$tm8Y;^N4OGY>U%3O5wuS`WZCz<FK4(v+Zy1{h1'
    'im0##)zcOmvht0J`k|{snVVy(8?jieI*%xEv(>lBx@ckTdY5&q;Akx%^2}0~-i->NYUzavlin2+r0XKxH%t`-8d>I<tmii`V)$jO'
    'ZKg^-ZD<xw36TY^>5c)ArSc=QBzXtiCEUhm7!DLSd1v1s=#sIscT81HQZ$9op$Fh7HD3iz5cV-FJ>5f|&;|7xYhBHl27jhcO>BK#'
    '(DR1r0}f{ckaNsVxLiT6#CmPuSe9IfZvS8xYCN)sZ_qr)4i4iov1Ptl+~qD&-4LU?#_zGb`l{&OMFY>!1SQvC$(lENAS=jdQ8R&K'
    'fL{yKt#B&tnR%I{G0u9UhX1!boSxY?XkVdi`DrvJv#59b5J@r)?N?V9{D<y&_gf@g?rivI!r@TIgW9W_U%0N<KydqRe6l;N)(v2k'
    '9YBrQL5524Rv$HU7Gj=4sF1rFHJM<hE_Yk)CN_UG%xMWFR6eDq!%X!JQk$s^r^a`o@MXthUlntSVj-2uUz`SHyb+w)cy-4#6Q$PG'
    've~$z-ZTgT_@hznGnE9A>}qs=CgxfGIS#;&ohHu6iT89M4H3*LM>#Du&_U6f<s73%H1v+A%-F#!swS@C4T3J`qd{FqViKyW{Pm{3'
    'XbHY!^2kc2)#x)c1RZN=9;ddfdI3h*%y2h+gqkV0#|cN+pZYwDNGbza!M>@Nt5<?bS2KKdnh5M6ia(t?)-TonDk;*s;a~pz>&@9z'
    'c7X<mO!b|KA!&+2Q$G&1_V8;oL0&bJH!P+)Q=wm=5H&~eH}LGAnLTfwC(mm_>DFj&tAc&?*fRrj(Q8&vkNBvUih!d7A}FDOgVB@>'
    'fQpdHt@qz&x$|D*L}6ooYR0sB^s5CQhOf@>1W+XaDEA^N$yAdeB`+oQLXZjNmp=aaZIzO9Rp(@fQrndER041$SOXBz&<r(MQB4WX'
    'Qq9sEAW+i_>M>Szz9QtJrCV2Q*soeCWHt#k)!kT9@>qbbU@AL0jfV_)Ud|Y^u0<Uz*9P#tDCAk<{KA2!q~5>kRArZXq6ea~=GcVf'
    '2>rUrF2_7`udHBAFB$Pj-t^m**Q8u-IY#Ie7f;{^0?*3Y21vuJ{mDp`b0!*VVmr)EeQ5_wMaf*mRgmhfJC)mM^Hp=hir%sT6**|S'
    'ju?Mxo_|7>u}2WN5_O@PP;&x7sAeeb&n?j7>vAqyrtrWY+nl%AXr?+740No5qu|Z9N^Nm~9uyT;ta{l~bGW4e#xA}n=V>I~8V$Gz'
    '@-e`PtOnQ#w8eyvAJW{aNh8r%4R^|F9h)ICEr!w5Nyfh-<Pv}9^7;hAJpsK1bp{*9G$%p^zRD>voU_wF;b=hEO^uk;AhYD#*W))*'
    '<_UIt>U~VMhYD4-W2<#cK-h#^qI#%zG@6PIy|1w!4~4n`o@$-*ijWId=QoV1DUu$0&l}XDDjBxk9;mzxB3CU&Qy-Tc3Zb?tD@Zx;'
    'HNEIq{=|fN^QY#^n_KDf1TwE)nt+4|E6Q2()pTtM+SA2_#-&at6y}Yy8e%om)<h?W0#@o@5OO11UW0&x(pHvQRp2C{?_p>r8AKC|'
    '3Cr$jDEP&yVF?vi%+U*0YV7k*PV!c#_RPja*pgKlx5nJ5?NCuuGvBJp^Mf82(^5f*LCJS;no~A_WclYugP$9<$FTr_P)lb9A1XX;'
    'TZ2bbO+aJ!D-<o(1vk?rsZ-zkG_smRYc%@V8XhO_;%d{{O1>Y;)Qnb_0tLj;LDuWY&($*z<O^5KS=(fFg^E+tY}a@y;yO6g>3M%-'
    '*g&${-)_ZQd(q_L0PhQ~sLqRRR2MhTy1G-IGyJ{%eE)T;OS`6n>{yAVX5ei(X)yu~L{)3asDY8Vc3nFtiC0N1fDy;CfkuA(jCpP~'
    'X%B6=tXq?uUFvAErn3#a3U-J8sA~KKk~<m+uwtkZ7(<RHsmWSBJ&o&ec&7>H%BLJY4cr1`5jL+`y+t>O8jXJRG*3%+Ys$wV4kvZ)'
    '0Q2u~F5?o}fenx<ytdPIG*JlbutW6T@NeZ=ZiqM9rMXq`tj;gbm6K+qHve;G*R2lin%%CqM@tmkD5nO`Z(W_Kk&4sUxT&d;u)0+&'
    '-@;8)O*3tprn~)I5O=FVd-2{EqF!q%O!KM33`($iIO*2o1|^l47(DP3zv5OFsMnmmfeM_eu7B(}LuAra=40B_cEDQyt+8r~%DInL'
    '*Dr?s7YOoMrLWi?n1KKmLO+*r(JQufZOD?-=xJc6dd6lDfz`8)-i--eimx#jHH1?}V%|puZh)e=Ga2DAE2kG_B*LxNaH^Fzn6z#9'
    'Moi3$X+rS3hFvL%V>=DXSlFPp7zBSV<6`G2<5E*nUa_fa^2us?RB!CGiGc(z&AL+(tvXMmfkgD+48CWeB>POp_cq4>#>R0gYb60U'
    ')U?ScUrC7ysINLGS@o~ca15ntYesGWCwGm!Fy}HZS*UnZQxnD>F*x<_+6mym8R26`2Ir<awhBo#M=|MMs^c=dQoWRid?q73_Whr#'
    'MkLB=l0=I*i-9eu>O);WOKM9w##V3oM`|xleXDgk=rQ9L&t+UERG~ts3YucPRWoP;oDoPgzz+_(J9R{D*{W}|8smU2dPZ@H{5OV`'
    '-0I5?sgLSFH#K^35!VC0T%G-J9*~p{gT)0Wi{b&Ng2r_N^WYK@l`cQ$w%lsVuKCaEra~OtzJ|U75w~lYYVHM`4qR+wsX3pR0~Aw&'
    'bf2RNHS=>d$&Ie;yLR>A?dk`N&kIJa_KHkFs6&GtY7)Ivg}PITs4`H!-s;_QEbvss<ut^`IP#_c%c`SZK#iutaAX7$97w5}(b&|N'
    'ui(N)M*}NJQjcvXA|6xk3gja;B`Io(o2y0@235n9(CN4akq`<ZLsurR5-#<i%nI-XX6927*Gj9N`8I$)qb!Iz333=q#PdbeR*l$u'
    'N4XFeRmeB-QE_W}hBxKLCCQDB>?kFgDo>((WsS+*VAsANqiSOr<@Pk7Y}FC)2I^bY)UR9Rv@9&1inw^(;s^_{`EP~md^x3g@MBHb'
    'u`L?uTCsvx>hr0dRA3-zqB3(}xhEpxWBFbIf?4s4mRbZkiNHVtRL-n|03sdd3N28|anzLuMi<pP6Q^7Hsfg=cgW0fDO}fETgl@db'
    '=MtP*B`_iy0pOF>2TST5tXE3LY)1}gK8@p7H)V8v4Y?FIYe$+&s^G(=%Y+3PQ6fNBvb57}z(hA-x<)0Bp}V}!MO^dDE63}A<~Y}='
    'wqQ^W<vG?&{K%S!3&F)}!1@kOsFwD@!!>t-*PVIv$832u!h&T$^tPJZ`i56c>d8giFvN-Fl2P;7Hr3Y+lBxO;%>wfcf*VgoTr^69'
    'X`>Q4b@`?@p*q#s*5fHSbHMwUV1Jl%FjcKgnp_F~Nkxb&ovq@r6FWqH2^4G&0;jF^zf#f!`T#UTp=vLc4Z^V|4R*y^j>;d^*PB^R'
    'MO+Y7>`Es0d*`Bl3(6yawWx9{fXcGcTU9<mWUuNt0^3k-hUIwFLGUapU9;uO#-(XwQC_%mTvZGSl~FTG&Erz{Sk>B0^hvEyM~KS&'
    'SGpt)w61<C;&K-1vNp0RkGwj4Csa&R-IlS+a#nXy2TU2Nds5<YkE+1X{o?B5M*YQ4UDyp_Qd3-U2x?nq)Nm6b#vkS5*|dVvG<Ej{'
    'S@D^%ZZ!#?8i8CvEcQgeb#kVUx@@i*AE=I{x<!p@2W@K`91meh&-z3_s~O%{zRFVoI8n1t#>)b(JfrKCM)NWojvMeY7hcw?ECp(T'
    '#|`RetXvX3_eBjR?`mwPD^fHo<}(S`d;6hVU!|^duuXZzzT+Cyd<jBu%vQ$9<7qdJeW;gS1=7J`G{-faNO-Ks)x$V0b)%uJ9aCnG'
    '$z@r?FNda#`kV~G6<~udY36*`*r~2aUC--G!nLi`Bs3^{pZ(^~F2GFWdkcyY3E_?~*{nO5dbHT|Y!u8mY50l~a9)=1$S<~2I<&fM'
    'EZujieETTm0Hkr=ju@>OcGVeBhZ<GrCO`0vaE>^rb|&GPK?!H;MH7F!Mjtj^5hZinpl>j9Sez1Ssv;T9hRI8aN(8g1#=XBR;To`m'
    'b*dUA)Z3%E-6*be*lOadlw;*|s?8Tn%`M%t);$`(32I?grD@J2TwYbDx`nNwmfjl9qU_(i`Opm$*6c5wQUb}~2%)aWoK%1)!94Tp'
    '^%f-fdGz;Cdt;5Mtrp&^3QYr=Q44EXo8~EcRkG!(5lz-NRYA$Ct0mQqN0}R~*ZJa+uN5coNq03~AF0VjU}PLg(F=^O!uD}LrHKll'
    'w240HTBp7s;@LeXNUUojvmX4cW6Koba%_31IvOI@<tS8|q+y=b3ye0W7_!p}<wU>*aZJ0QKT8ZWs6QjbUKM#x{bw{~su@kvkxLo#'
    '7)PrSFlAfSH<(-O>uDxCA2>BbOv6R=EJ*|fkB6kPeSj@nrvBO*)_Y`02R#>Lg6L*j;g{v5PrtrnTepOZ)GQvtIGP%SFB8$v<xzI_'
    'E2E^14T;cWb<L=4wrL@)F!7&1{Unn;PXcCD=M2;j>?9Ulo!Tha9^)bu#ML!+r~o*WE1*~;mJITQN$xiayR6KssumuumaTTG76yKm'
    'gKCB~07zAbxnl+z7-)MwCUtBHwM)%qPlP>9d-;}if^}%10kjddu^dOqNBekVx}LQ*C`YlBKwE89a$C)6=oM0URm_Ebt34TxLHU}*'
    ')}leAMTtyR`9c~n8=9cu)Elb4%sff~lr}<KDdkMePaW68{5D4zeI07Y!q*hW1=2>Rsh66Y0yt3~0xmtMm=-abFd$!F-zMvFms^$5'
    'uW>)bT(B-N#S@K2Z%S4hY6qHxB4O(A@}j1>Fe)5P?ps$eXR@BW4dNH&4?$<siJEVm6aF4#7g26yY2b7=&N=BuDMfI-?!{F2Jo1go'
    '&==aR`=ahs$pFPw46FygwXGhEPylu)>DOiS%d2UcCZFi#&hK(O(eKy9+{i0SvQCg~CA^GTgB)2wkpoULzzrhk#Rh_wroF%xy#rXF'
    'rqKp9fA@b#<R$(e*9tjg54sbB@I|Hm);l#qs#Q-h{$QfeuvYcRXmn+(NF_{uBJxkoWdB#ST0if!uB=(9ZnS2h%b4q&NfWQ&zo<fi'
    '&SwqfnBel`7D?@HvL5)NI{K}*N_*_7V9>as#<v1hNy!Jg#feHr#?>zM2!$tal^-fK8Q0N*217T!$(%_0vCsNeU7lWzL2JY-4T}z?'
    'IrV#tEveQ%^Q)kB^~%|*#)l&aVLM=Cev_nYrbsPjj;Y#|9E>lvsGo40xv*jC+2Y*P03}E)O2hJV26*AiYAbWcH%Pjbz-iUv8L9__'
    'VtMLJ;-{)c)u<q5y`MBC7}cYTYSsG`JFclNx*YBGk4n8nv7SR$TKrUk2EehFqK<G`Eg<xyLI}u4twNxTq32rFo+zmHS`7Y7Tb^XH'
    'PnEaSt}!mUmBfcAAx@S0c^cvtKz}ve>*fjMgDZ3!bdkmqCtICflX894-WqaCC^>2!ifEfA6zQ65PL13+dW96-T`Rq7)|haa*3YeQ'
    'wqKF*9Q5gqumky24x$;3{{QdoT6!GEk@SD*^G-o;%X^b;+SZgTdG*MwtcxJY7G{8&u0~fiu)w1K`+gtwq9odxbC|x_!H_5hgYhC6'
    ';Xq%(sIZy5r>gNz*g1Iboypc|88R{SreSFjO6-sI)kps-TI@Wu3!l1a!Dx3ZhK5^Vyo>30tQO9T5Lg*-z<K+^kU5wjxLHW?Nnfk{'
    '>L!yNF$7@2DPaX<2J>$Y6I|J@!>}pdiD5GP<;`*|m~}NIg2m!@t^2XA28*M0yQ(p;mAJKy@87agI85|y&8mV>m#%g9J(}k=4-0KQ'
    'CI|5Vw0-kwSK~?cjqSqXW6ljs^=1o2#Y0XMS+&t%QCqT_KyBDq>0mo>J*z>O@HfDq+q?5#FGHpuf6J;kj%B#+20jg}?iOOuxG{WY'
    '@aP;bRLJU<chlgOq1S*Fxt-s_#eCAs>Lhzhfz+Ts#=~0~f1o6H$Z9tx2KFoZv?e!VErY46T3)e)z~m{w_CsCu>&a#PQ-ejgTaW&j'
    '#q(J_6=(9Cvb_Gud!h|QqXUl<Glqrh^Y&Tg!tVY^R~kWBZJ5oOa|F4txzjUu;TCK+aI;Kikb}HyYh$y)yfVvId%x2!ZMbU(tK0my'
    'yX_;QJW1jkmPf_U3g9M>Rhk~j?J*dA=X`nf7PQ8qfT+6HM%}m&{!H)s%mV%opJe~*(yITZS=Im2r0UOds<XC(53NPb;#jP#Ty0?1'
    'J1vB<>3Y0vv2kzAhhR5YfV=|4GlB4F9D1DY$NCz^<v<<6`ErfgxH&e1MH?O##;maFu0?wTW<d*b-dJ}4WAfbwWB{VIdBz*_$cx3~'
    '-mcG!8Q_Yxb?!Cz!-F4GL1b)rtj*a%o!xJ;qK#uyS+@6P>p}Qy@_;`~sNVAMVr6d)h&&hWnR{a((7V~K#}s#%5SjQHe17)~xaGG-'
    'CYxyuli75)9~jZ>{2VM<yRI0FCL=bGP4EfK>Q-4d)7aW(OQ*u|nQjQKChycWtki5&F1#k|ZhpACesrv3V#}~6O&0E5yPjXetiExe'
    'zPvey#Xujdws|OnHGDiKA+X(>?>+5qw!Cgk4$7+s6|}y0$GD*9YpY?Fn|kBg9<M&a`teu;rQ#M|17{R7>th1$n-BHWOG2y_o?f-A'
    'ig&;8I5Qd4Od?n_zoyJk;;}X!)7-$jc6j31Y2j_a3AUfvgsKSzJ>|263k=SdflPSzeoH@vHSB$#<w#tNm?uKry4siyZ@aTR4+~mr'
    'a<)H6r4EBy7O;jxY<Vo2RLde>yw13_Era!LH?G4VL}T!n7&Cxb7+%ZFq|H;idVZ3<_6>iFHQUszTXe5m$Xy=wsd&F_(=g38H~6g<'
    'E6wITxfZ+!3&CW%_}1Tiprhf;BlsG$=dL(69gR(G88z!V${ZjSZ_qK7ES3>XthT)|MGlVvFd}Vg<dJ$392N@W-0-g*kF{Cg<inP@'
    'ZD4bHl}tE)Ybs2Y?=iu-=|T4ztR>;!AtNBzHE&l>f-}#CMV+vPs&Fu;4FwKw7Tir<%|X}Dz0Hl)rf#0Mbq$w!$SZPx?d$B9*-3Cl'
    'ldA@l_qWX2!~}Rv(=Zp1W7gKKWsy@F<PAiDWkbYOa1QT~(>JE1*72_HB)IMQSnsxJZfW;<JUUfDRJO9<%C_-jkxXv-qeIIBTfl~@'
    '8%T$Scix(JI_Y;1pfs6=mKA$!W-@0c7YNcjRUbFcc+Rz=2K5~l4m7xzWI;Vac*5<e9(FQ23GT|OvL4G{ZRqgI?30eqFdMu+7PPQg'
    '<P>uGe&;n%3$CW+TMW8X_Qt=z*UPXQ0vqPea+-Mi#)hxQva?tZE>nj(K3qyz3%5H94ufnC&!KwEYF@!Qfv9}g%OXYqFTAoga9&xk'
    '*<%GF_%K^un__jgibt36-+-m!gWt9+>}&9>k4yKxa;o?H8MEWSq2$Gf8xv;XyLrW<t(rS)tkz6)3+3bbZC%@-u;IZxX2k@EYX)81'
    'hyAFUjA_?wkR1bo6^<o%W8E=cyLOn(34$Hk+c3EQ37>rH-&m4>-gzU_($w#CGQ`?U%&o(O;+)cEb8|5ugFC8_T@6p|($;~Nvzgl2'
    'fGXvcEoP6kVJrI&JDFYkwwxP}F5Kt_d(3}Cla{%mFug1d1Fgxrx*K|a7E5A+UyOzc%fRbT$fe5p8uYrk+<k{^2gkB_ZY$&R?$&83'
    '3gA6huEkOzU=y$zjc^8K+goKj4>?&m3BKheXndH(LbMhpfIP&@v)nOaAxvg#c0Gd!f3P~3gk@{77EcR<zV$U0?tk(mxHfURW6GU1'
    'lbOcaErtV)Xz=(IRI0Htc-v%SnIO9hGTx1bWu<tNh!Hfi_1y=ZXikFj#&Zi!fp|5j;%gQHqhARB&4wRhY?*wuZfmCSbv?#=9LCOp'
    '!5roFFMs~mFW-Or`Ooja{`lp;|L>O|Ov__(D)&)R9Nx+}2{VCSe*XRkXKrjwq9hfEG73-Uyv)wQG0$X~(r-Uhv`Z+;gNU;*e=B!U'
    'A<A=7r2OVjh9UD=>JY_?h{O2Qqq&dJF5w|&@9*Lzsq(tbBeXb4;vPr3wmO_N2zwm+(QC3edF@c7{#Q|sK@=s&V|fmuBM5gSSvQ}I'
    '2UuAir1=UX4c;PwQI|Wa_)mtutqjue@D$<iMSP0HQ<Q$Nj}bW3x91qI!R0EcA>9iNMjnKx;K@6BvJMmDKEW9VEfMW!h~waiYCc7s'
    'X8fJb2V%%q8c#7tq5#u*hB**N_U`F0bu48YdX8B#)+4-brN|R1>$Zu=zW!mAxLa3`G7|MsClfJ!^8rRr#7BH{Qi$hkKcf>vw)Xw$'
    'G)zzbusTe~(4&mQeALM_O!vVnCSK8#DpiKr^^%lfTxLZa^`dc*+xaEQu4j>oG7It|4e~IF%UzOWIUdNzRW{JGNRu=ucPZZzi!56;'
    'N%hc)|3SDqO7k%l|5J!~zkcT_O^SH&q^Y#WNqPw)fQSE32kF$@%h&#28l)n6ldXADiZ9nBD^elbmH#E!eu-1=!XWF`)~e|ee0kI&'
    'XY_BTrU@h!J;6%A58<U7x*ieD#Om;Axk<!bm|R5p3WT)Nu+s$gO(MuKiRBCUQ%<3R5GDK1ojTri9AnR+9jWDMAq5&v6Qn@X1@x_('
    'Z&k(5;AIfyVO|`hM%ng*(IS)<N9!9Jlqh(^(5#1jNXF@z(fA|}#LN+3hNRaCVckQ14)>o2cDAQaC`Fu0(R>aL$?Ia2$z?|5<nqQf'
    'xWtjb<x(0NbxUlQqz92MUxgv=N3%+uo8(K!5rg8N!A=@jI)jNzm}MQmOu#6N!BXKNKXXoc;3bi>IQ+zVj0KwZa<%kiz@OQWdI7c%'
    'UPM2j5fPAKsjK4|xI%!Bqw*@!>?-zo2y7{nh^Syid9p7qMO3KYuxeA8(6)?@#dK(wU5E*XA{5uGAGro?pFr>gC$au$1xd9}%!fD-'
    '(h<5#MSPO9HhX%laYtGWALr=tu4#A)(zgz+l<%}Yg87yHj~+1#zdj$Ux_U5LSC91?Lb?aNa`YZff<(ili-U}jt7!W$P2Zn|IaZM_'
    '2>cNBuw00Gr1g|NMAj0H7#eo%z$&MD#+*}ojHlz)jJP+*=17Ot3SBh6KS~`>dMlJCB$zYARyWoeFF13I2g_cw<juvB|G;vem@Gbt'
    'EPv9^h@tlA=sQ$!|BbGn%=ilRArEUX-<jgo6HjM*g*}rl@^;fRY(1N-&{RQVTkjyx*vo!rLgrHE0rET3@E-9$O7f2o`kIP;a=8|;'
    '7-6SCQz~DlXVkS~aE$k`zCM_^r_(jFrL-6yn3*S9AWmQpM8zKKXEj)*O=ntZu;3^Rf^egtWT2@W;_T~kak;{~h|1zhgw9JD6|u%j'
    'oWU2CQ74wwaM|q1Sp)3)IE<p8`+EtlWZgUscg5V+->2eQWcwlt=w-fTk6X0lS>p7(lBD5R*^BiA>#K-E$<&e_MV7<5%|5@WLmGiv'
    '|M4Jp1$Z8QZ6s);Hs8N}|NZCvf6cM&B@}e=rayxXdJvgNAx(q$fG$$SE<c=0E{sDOyu!80@&Hz}EXOFPRc$>6uxX=lW%dYRO6xO;'
    'G3Nv6)mhTEFXq|mUextHjA43#0;hke`^q_v$j=E^#T-07Mtr)0;GH}T;!N`S{!K=CcESF$+Ie!E|AURbm%6TZWC_yn3BlbwWrG+I'
    'hm$DJpuiAb7~VQ>nATM{w88t(2t|DKIf(NM7wHL0E!Q%Qpd{&)=}r&s+*{jTfWyh_Rl(^Te2QdLq30$s#3y=B+Hz;W(_~Lgz)%NF'
    '=bxGtaVd^Ru#(!>!t)&AFJYR*k}jz@Mjiht2*-fa9Sa!`fVj$K&}3-*5(Lw~QxFAT)Fs*hbaQ@6xjuAL(6vZ;6m$-n3O}Vn#O1jV'
    '7)f}{$MA{Tng#jj-5Kh`khM_IkK3n+)w0`MU`5WKGpE+qbI6zs2HPz(-koA&9wdXb8<np;G~mflE{-mWAz@h*?!{ohfn5)05zRta'
    '17TNfIvSsY^st9(eNesfeyXN6I>Z#d+O{lH!C84UJp}+6k$?i1au>#(4K;)2L3$D$vB=nAypI@|89!M!!2Z=?d`x;W$VP6gl*cH@'
    '&J#|Q92hw$1L@Bz@as$Y5-tBv1S+vaCL*m`J5GOsKoS3%q|vjN6!Af%T?BH4HJY&0Y?mM|z*=L_uE@ZKd&$PL#W76$68|NL3NfyN'
    'O9D;<kN3*7XE1weQ)HU_--#IC9{z`m_A4e`l!25b+5C%o>Pj?4%3YE!;v32RxAC(lsE$*6>C#C-x&P5aG_fuP|1?R>9ulr_(iJ3|'
    'KDLLD=v~}%5e$`^k|jPInmre3A7~#N89;`Ty<X>wGft-9a*sRm%%c%q!!IHVa-p;OR&5Z}SrQGln%pS9%IM+v1ni=Nxb_aaB#;D6'
    'wt!r!ge?@W1j$|&uj_y-^t4;*r(FRaNi!Mhn})q=mLcbLJKT?JkZK+rKV^Uy>E|&1`y~Cfa2<g;ce|P9Bcx2O!G5%?+yQzm4u@`('
    'BW`>E5uSv_p*z!H-@~RqaO0y{tM1z}0ZUxGh)8A_lB<;i7b!(t#9*alfpS+II)U2v`2?x#rR`yfT?Q1a<L{Dsr*G_h#HHc1oPwSa'
    'WIUUzNd)QsJPDgl*7r!mavB(^2VeKc)%yVJE&py;M1~pHZE^v6x~9<-{A4K2A}%gFZs+J)vn<V5A41?LqfG_-WoD{pXL=q>^K<~I'
    'f`|^vs0tKBy}6aah~QG6%bF|}QPjDyvz5tRsu>SUu{Nl5jcm%Ywdo;Y<A^2QvrbH>S@(TCi7|*1Im3U9l0hHz9#4-1pALLbNblMi'
    'VB!claZy87V-x$Jih&h-rMamPVb+|VAqCHgRKvqKv~DQD*%=yDeN(m=Fl-fd68Kk*f%F!42iNscX!LIXRW!zTb5wC!{7>iq!};IK'
    ';_=Jk@yp`zc!3`;@M8r(R}FalDdzt$RWxPB_0w}0nmQp!hjAZ;1Dg#`iZ3~>lOxq2XK)TEqhLoYc-vj<;R5L&W>Rzxj7ps>Pa7~a'
    '6-uY>c%6EXb`q5aH#vLXhJLp)i9?J-3cLmElFT|Az#xta4Nou-az!l-5FHDsCMT@C72PBQy(ARTFHqhzHbmTMLk+YH&LTK0pBMsV'
    'a!<0`z72L+l3qLOsxzN9+P2Ol9rQ2I935J}O|EZSy+ZpH{dtG2wr%+e8O-SSp@SmJ!@VkF=M6fdcNnm1u2Ha;0%e+nu5A@bc+&c~'
    '2QnHM<jeATOfT3W9glsPnwAyW>=CJ7768xwD_^8K62nuk!Ha5}D8TY8<Wm~^WWv&&nNV8c%62~R;H@fcAaW585zh6a++Kx=Df)2J'
    's$B+P;l?Gep(>JA7?|!T(zmb76M#wMNGypW!B=Wi72F$e=i(9`<YB?_bcUg89tk-Hk2?cmKx71$<eDG!c!crZ+hmkl{~Fo!@Oc@3'
    '7<|%k^+<oCb9RT}OVH@y&Y-GW+c$LQWe@-S@w581xp#u+lQA}2s^8N4kMH09;F-=Y<D5J`{Kv1?>gPYw??0=);cs>qasT=K*ZaS!'
    '-`GL-tNix;=g<3(sj2*SZ+|{(|DBgk*9d?9`TvT~eop'
)
PINNED = json.loads(zlib.decompress(base64.b85decode(PINNED_DATA)))


def record(text):
    payload = text.encode()
    return dict(bytes=len(payload), sha256=hashlib.sha256(payload).hexdigest(), utf8=text)


def fixture_report():
    report = fixture()
    report.update(benchmark_feature='live_pixel', method=live.METHOD, selected_flag='lod.enabled',
        initial_enabled=False, restored_initial_feature=True, atmosphere_guard_absent=True,
        initial_warmup_seconds=30, settle_seconds=8, measurement_seconds=20,
        core_protocol=2, transport='core_global_only', raster_policy_proof=False,
        sampling_policy=live.SAMPLING)
    props = dict.fromkeys(live.PROBES, 'false')
    props.update(benchmarkFeature='live_pixel', gpuTiming='true', cpuTiming='true', gpuSampleInterval='5')
    report['instrumentation_properties'] = props
    mods = dict(minecraft='1.21.1', neoforge='21.1.253', iris='1.8.12-snapshot+mc1.21.1-local',
        sodium='0.6.13+mc1.21.1', shaderlod='0.1.0-dev', shaderlod_dev='0.1.0',
        fabric_api_base='0.4.42+d1308ded19', fabric_block_view_api_v2='1.0.10+9afaaf8c19',
        fabric_renderer_api_v1='3.4.0+acb05a3919', fabric_rendering_data_attachment_v1='0.3.48+73761d2e19')
    inventory = []
    for index, name in enumerate(mods):
        if name == 'fabric_block_view_api_v2':
            inventory[-1]['mods'][name] = mods[name]
            continue
        path = '/fixture/' + name + '.jar'
        inventory.append(dict(mods={name: mods[name]}, path=path, uri=Path(path).as_uri(),
            filesystem_provider='file', identity=hashlib.sha256(name.encode()).hexdigest()))
    report['loaded_mods'] = mods
    report['loaded_artifact_inventory'] = inventory
    report['loaded_artifacts_sha256'] = {r['path']: r['identity'] for r in inventory}
    report['loaded_artifact_evidence'] = dict(schema=1, loaded_mods=copy.deepcopy(mods),
        loaded_artifact_inventory=copy.deepcopy(inventory), errors=[], complete_mod_coverage=True,
        complete_identities=True, valid=True, limits='Synthetic inventory with actual Java shape')
    receipt = dict(expected_loaded_mods=copy.deepcopy(mods), loaded_artifact_inventory=copy.deepcopy(inventory),
        loaded_directory_identities={})
    report['workload-manifest.json'] = dict(adaptive_pack_sha256=live.ARCHIVE, source_world_sha256=live.WORLD)
    for phase in report['phases']:
        enabled = phase['enabled']
        state = phase['inputStart']
        state.update(actual_shader_sha256=live.ARCHIVE, framebuffer_width=2976, framebuffer_height=1672,
            live_pixel_control_receipt_schema=2,
            x=500.5, y=68, z=660.5, yaw=87.38, pitch=3.735, day_time=12500,
            live_pixel_dh_present=False, live_pixel_camera_type='FIRST_PERSON', live_pixel_fov=70,
            control_sha256='6' * 64, shadow_coherence_active=False, shadow_coherence_configured=False)
        controls = copy.deepcopy(OBJECTS['volume_effect_controls'])
        for control in controls.values():
            control['mode'] = 'INHERIT'; control['repaired'] = False
            control['policy'].update(enabled=False, mediumWorkRatio=1.0, farWorkRatio=1.0)
            control['requested'] = copy.deepcopy(control['policy'])
        state['live_pixel_effect_controls'] = controls
        state['live_pixel_effect_controls_sha256'] = live.hashed(controls)
        state['loaded_semantic_config']['effects'] = copy.deepcopy(controls)
        state['loaded_semantic_config']['rendering']['shadowFrustumCoherence'] = False
        source = dict(qualified=True, source_fingerprint=dict(sourceSha256=live.ARCHIVE,
            memberSha256=copy.deepcopy(PINNED['members'])), complete_member_count=533,
            complete_member_sha256=live.MEMBERS_HASH, option_count=368, complete_options_sha256=live.OPTIONS_HASH)
        state['live_pixel_source_qualification'] = source
        state['live_pixel_source_qualification_sha256'] = live.hashed(source)
        state['effective_shader_options'] = copy.deepcopy(PINNED['options'])
        state['effective_shader_options_sha256'] = live.OPTIONS_HASH
        main = ''
        for table in ('lod', 'terrain', 'rendering'):
            main += '[' + table + ']\n'
            for key, value in state['loaded_semantic_config'][table].items():
                main += key + ' = ' + json.dumps(value) + '\n'
        effects = ''
        for name, control in controls.items():
            effects += '[effects.' + name.lower() + ']\nmode = "INHERIT"\n'
            policy = control['policy']; bands = policy['bands']
            for key, value in dict(near=bands['nearBlocks'], far=bands['farBlocks'], transition=bands['transitionBlocks'],
                mediumWorkRatio=1.0, farWorkRatio=1.0, preserveSalientLighting=True).items():
                effects += key + ' = ' + json.dumps(value) + '\n'
        snapshot = dict(complete=True, errors=[], limits='Complete ConfigurationFilesEvidence test fixture', files={
            live.MAIN: record(main), live.EFFECTS: record(effects), 'options.txt': record('renderDistance:24\n'),
            live.IRIS: record('#Tue Oct 06 01:11:59 PDT 2026\nenableShaders=true\n'),
            live.SIDECAR: record('#Tue Oct 06 01:11:59 PDT 2026\nTAAU=true\n'),
            'config/unrecognized.cfg': record('do not drop unknown bytes\n')})
        state['configuration_files'] = snapshot
        state['config/shaderlod-client.toml_sha256'] = snapshot['files'][live.MAIN]['sha256']
        state[live.IRIS + '_sha256'] = snapshot['files'][live.IRIS]['sha256']
        state['live_pixel_configuration_semantics'] = live.configuration(snapshot)[0]
        state['live_pixel_window_state'] = copy.deepcopy(WINDOW['capture']['start']['state'])
        state['live_pixel_window_state'].update(framebuffer_width=2976, framebuffer_height=1672,
            window_width=1488, window_height=836, focused='true')
        phase['inputEnd'] = copy.deepcopy(state)
        phase['windowState'] = copy.deepcopy(WINDOW)
        phase['windowState']['capture']['start']['state'] = copy.deepcopy(state['live_pixel_window_state'])
        phase['windowState']['capture']['end']['state'] = copy.deepcopy(state['live_pixel_window_state'])
        count = phase['windowState']['capture']['observations']
        phase['windowState']['capture']['focused'].update(true_observations=count, false_observations=0)
        phase.update(endFrame=phase['startFrame'] + 300, renderFrames=300, settleElapsedSeconds=8.01)
        # Separate capture intervals, full-frame N5 cadence, unique positive rows.
        phase['startFrame'] = phase['index'] * 1000
        phase['endFrame'] = phase['startFrame'] + 300
        phase['gpu'] = [dict(scope='game-render', frameId=phase['startFrame'] + (i + 1) * 5,
            epoch=phase['index'], milliseconds=8 if not enabled else 6) for i in range(60)]
        wall = 1000 * phase['elapsedSeconds'] / phase['renderFrames']
        phase['cpu'] = [dict(scope=scope, frameId=frame, epoch=phase['index'],
            wallMilliseconds=wall * ratio, threadMilliseconds=wall * ratio * .4)
            for frame in range(phase['startFrame'] + 1, phase['endFrame'])
            for scope, ratio in (('game-render', .7), ('render-and-blit', .8), ('swap-and-events', .1), ('run-tick', 1.0))]
        phase['timingSnapshot'] = dict(backend='ELAPSED_ALTERNATING', renderFrames=phase['endFrame'],
            samplingPolicy=live.SAMPLING, sampleInterval=5, pendingQueries=0, scheduledSampledFrames=60,
            scopes={'game-render': dict(samples=60, totalSamples=60, droppedQueries=0, zeroSamples=0)})
    report['stable_input_reference'] = live.stable(report['phases'][0]['inputStart'])
    return report, receipt


def rehash_state(state):
    state['live_pixel_source_qualification_sha256'] = live.hashed(state['live_pixel_source_qualification'])
    state['effective_shader_options_sha256'] = live.hashed(state['effective_shader_options'])
    state['live_pixel_effect_controls_sha256'] = live.hashed(state['live_pixel_effect_controls'])
    state['live_pixel_configuration_semantics'] = live.configuration(state['configuration_files'])[0]
    state['config/shaderlod-client.toml_sha256'] = state['configuration_files']['files'][live.MAIN]['sha256']
    state[live.IRIS + '_sha256'] = state['configuration_files']['files'][live.IRIS]['sha256']


class LivePixelControlTests(unittest.TestCase):
    def test_actual_java_fixture_when_generated(self):
        # These new Java source-test exports derive retained r3 inputs. They do
        # not repair or accept any original r3 runtime report/contract.
        directory = Path(__file__).resolve().parents[1] / 'build/pixel-control-receipt-r1'
        path = directory / 'source-fixture.json'
        if not path.is_file():
            self.skipTest('Java owner has not emitted the revised LIVE_PIXEL receipt fixture')
        state = json.loads(path.read_text())
        semantic = json.loads((directory / 'source-fixture.json.semantic.json').read_text())
        self.assertEqual(live.stable(state), semantic)
        _, parsed = live.configuration(state['configuration_files'])
        live.validate_effects(state, parsed)
        for field in ('live_pixel_source_qualification', 'live_pixel_effect_controls'):
            self.assertEqual(live.hashed(state[field]), state[field + '_sha256'])

    def test_pinned_full_maps_and_complete_gson_shaped_report(self):
        self.assertEqual((len(PINNED['members']), live.hashed(PINNED['members'])), (533, live.MEMBERS_HASH))
        self.assertEqual((len(PINNED['options']), live.hashed(PINNED['options'])), (368, live.OPTIONS_HASH))
        report, receipt = fixture_report()
        result = live.analyze_report(report, receipt)
        self.assertEqual(result['status'], 'QUALIFIED_SETTLED_INCREMENTAL_DIAGNOSTIC_ONLY')
        self.assertEqual(len(result['adjacent_pairs']), 2)
        self.assertFalse(result['quality_accepted'])
        self.assertFalse(result['raster_policy_proof'])
        self.assertIn('OFF is not original/default', result['limits'])
        self.assertFalse(result['restoration']['postshutdown_byte_identity_verified'])

    def test_actual_preparer_manifest_schema_and_complete_pins(self):
        report, receipt = fixture_report()
        manifest = dict(schema=1, scene='coastalwater', configuration_only=False,
            source_world_sha256=live.WORLD,
            source_qualification=dict(archive_sha256=live.ARCHIVE, complete_member_sha256=PINNED['members']),
            experiment_arms=dict(toggle=dict(archive_sha256=live.ARCHIVE,
                expected_effective_options=PINNED['options'], expected_effective_options_sha256=live.OPTIONS_HASH)),
            benchmark_recipe=dict(feature='live_pixel', phases=[False, True, False, True]))
        report['workload-manifest.json'] = copy.deepcopy(manifest)
        result = live.analyze_report(report, receipt)
        self.assertEqual(result['manifest_contract'], 'explicit-complete-live-pixel-source-schema1')
        for key in ('source_world_sha256', 'schema', 'scene', 'configuration_only'):
            broken = copy.deepcopy(manifest); broken[key] = None
            with self.assertRaises(ValueError): live.validate_manifest(broken)
        for section, key in (('source_qualification', 'archive_sha256'),
                ('source_qualification', 'complete_member_sha256'),
                ('benchmark_recipe', 'feature'), ('benchmark_recipe', 'phases')):
            broken = copy.deepcopy(manifest); broken[section][key] = None
            with self.assertRaises(ValueError): live.validate_manifest(broken)
        for key in ('archive_sha256', 'expected_effective_options', 'expected_effective_options_sha256'):
            broken = copy.deepcopy(manifest); broken['experiment_arms']['toggle'][key] = None
            with self.assertRaises(ValueError): live.validate_manifest(broken)
        broken = copy.deepcopy(manifest); broken['adaptive_pack_sha256'] = '0' * 64
        with self.assertRaises(ValueError): live.validate_manifest(broken)

    def test_actual_four_coarse_cpu_scopes_are_separate_and_complete(self):
        report, receipt = fixture_report()
        result = live.analyze_report(report, receipt)
        self.assertEqual(set(result['phases'][0]['cpu_wall']),
            {'run-tick', 'game-render', 'render-and-blit', 'swap-and-events'})
        for change in ('unknown', 'duplicate', 'missing-scope', 'missing-frame', 'internal-gap', 'wrong-epoch'):
            broken, receipt = fixture_report(); rows = broken['phases'][0]['cpu']
            if change == 'unknown': rows[0]['scope'] = 'iris-stage/opaque'
            elif change == 'duplicate': rows.append(copy.deepcopy(rows[0]))
            elif change == 'missing-scope': broken['phases'][0]['cpu'] = [r for r in rows if r['scope'] != 'swap-and-events']
            elif change == 'missing-frame': rows.pop(0)
            elif change == 'internal-gap':
                phase = broken['phases'][0]
                for row in rows:
                    if row['frameId'] == phase['startFrame'] + 7: row['frameId'] = phase['endFrame']
            else: rows[0]['epoch'] += 1
            with self.assertRaises(ValueError): live.analyze_report(broken, receipt)

    def test_generated_timestamp_only_in_two_exact_files(self):
        report, receipt = fixture_report()
        for phase in report['phases']:
            state = phase['inputEnd']
            for name in (live.IRIS, live.SIDECAR):
                text = state['configuration_files']['files'][name]['utf8']
                state['configuration_files']['files'][name] = record(text.replace('01:11:59', '01:15:12'))
            rehash_state(state)
        live.analyze_report(report, receipt)
        for name in (live.IRIS, live.SIDECAR, 'config/unrecognized.cfg'):
            report, receipt = fixture_report(); state = report['phases'][0]['inputEnd']
            text = state['configuration_files']['files'][name]['utf8']
            state['configuration_files']['files'][name] = record(text + '#nongenerated comment must remain\n')
            rehash_state(state)
            with self.assertRaises(ValueError): live.analyze_report(report, receipt)
        for text in ('#not a generated timestamp\nTAAU=true\n',
                     '#Tue Oct 06 01:11:59 PDT 2026\n#Tue Oct 06 01:15:12 PDT 2026\nTAAU=true\n'):
            report, _ = fixture_report(); snapshot = report['phases'][0]['inputEnd']['configuration_files']
            snapshot['files'][live.SIDECAR] = record(text)
            with self.assertRaises(ValueError): live.configuration(snapshot)
        text = '#header\r\n#Tue Oct 06 01:11:59 PDT 2026\r\nTAAU=true\r\n'
        self.assertEqual(live.normalize_generated_timestamp(text), '#header\r\n#<iris-startup-timestamp>\r\nTAAU=true\r\n')

    def test_numbered_backup_settings_complete_and_other_backups_raw(self):
        report, receipt = fixture_report()
        for phase in report['phases']:
            for boundary in ('inputStart', 'inputEnd'):
                state = phase[boundary]
                text = state['configuration_files']['files'][live.MAIN]['utf8']
                state['configuration_files']['files'][live.BACKUP] = record(text)
                rehash_state(state)
        report['stable_input_reference'] = live.stable(report['phases'][0]['inputStart'])
        state = report['phases'][0]['inputEnd']
        text = state['configuration_files']['files'][live.BACKUP]['utf8']
        state['configuration_files']['files'][live.BACKUP] = record(text.replace('enabled = false', 'enabled = true', 1))
        rehash_state(state); live.analyze_report(report, receipt)
        for extra in ('near = 33', 'near = 32'):
            altered = copy.deepcopy(report); state = altered['phases'][0]['inputEnd']
            changed = text.replace('near = 32', extra)
            if extra == 'near = 32': changed += '\n[unknown]\nvalue = 1\n'
            state['configuration_files']['files'][live.BACKUP] = record(changed)
            with self.assertRaises(ValueError): rehash_state(state); live.analyze_report(altered, receipt)
        altered = copy.deepcopy(report); state = altered['phases'][0]['inputEnd']
        state['configuration_files']['files'][live.BACKUP] = record(text + '#backup comment retained\n')
        rehash_state(state)
        with self.assertRaises(ValueError): live.analyze_report(altered, receipt)
        altered = copy.deepcopy(report); state = altered['phases'][0]['inputEnd']
        state['configuration_files']['files']['config/shaderlod-client-2.toml.bak'] = record(text)
        rehash_state(state)
        with self.assertRaises(ValueError): live.analyze_report(altered, receipt)

    def test_canonical_derived_hashes_ignore_only_object_order(self):
        report, receipt = fixture_report()
        state = report['phases'][0]['inputEnd']
        for field in ('live_pixel_source_qualification', 'live_pixel_effect_controls'):
            original_hash = state[field + '_sha256']
            state[field] = dict(reversed(list(state[field].items())))
            self.assertEqual(live.hashed(state[field]), original_hash)
        live.analyze_report(report, receipt)
        altered = copy.deepcopy(report)
        altered['phases'][0]['inputEnd']['live_pixel_source_qualification']['unknown'] = True
        rehash_state(altered['phases'][0]['inputEnd'])
        with self.assertRaises(ValueError): live.analyze_report(altered, receipt)
        self.assertNotEqual(live.hashed({'sequence': [1, 2]}), live.hashed({'sequence': [2, 1]}))

    def test_receipt_schema_is_explicit_and_old_raw_contracts_fail(self):
        for value in (None, 1, 2.0, True, '2'):
            report, receipt = fixture_report()
            state = report['phases'][0]['inputEnd']
            if value is None: state.pop('live_pixel_control_receipt_schema')
            else: state['live_pixel_control_receipt_schema'] = value
            with self.assertRaises(ValueError): live.analyze_report(report, receipt)
        report, receipt = fixture_report()
        state = report['phases'][0]['inputEnd']
        # This older insertion-order export is deliberately not relabelled.
        from analyze_inprocess_control import ordered_digest
        state['live_pixel_source_qualification_sha256'] = ordered_digest(state['live_pixel_source_qualification'])
        with self.assertRaises(ValueError): live.analyze_report(report, receipt)

    def test_fully_rehashed_source_and_complete_option_mutations_rejected(self):
        mutations = [lambda s: s.update(actual_shader_sha256='0' * 64),
            lambda s: s['live_pixel_source_qualification']['source_fingerprint']['memberSha256'].pop('LICENSE'),
            lambda s: s['live_pixel_source_qualification']['source_fingerprint']['memberSha256'].update({'LICENSE': '0' * 64}),
            lambda s: s['effective_shader_options'].pop('SHADERLOD_LIVE_PIXEL_BUDGET_INCLUDED'),
            lambda s: s['effective_shader_options'].update(TAAU='false'),
            lambda s: s['effective_shader_options'].update(unknown='true'),
            lambda s: s['live_pixel_source_qualification'].update(option_count=367),
            lambda s: s.update(live_pixel_availability=1)]
        for mutate in mutations:
            report, receipt = fixture_report(); state = report['phases'][0]['inputEnd']; mutate(state); rehash_state(state)
            with self.assertRaises(ValueError): live.analyze_report(report, receipt)

    def test_raw_config_hash_bytes_inventory_and_semantics_rejected(self):
        mutations = [lambda s: s['configuration_files'].update(complete=False),
            lambda s: s['configuration_files']['files'].pop('config/unrecognized.cfg'),
            lambda s: s['configuration_files']['files'][live.MAIN].update(sha256='0' * 64),
            lambda s: s['configuration_files']['files'].update({'config/new.cfg': record('extra')}),
            lambda s: s['configuration_files']['files'][live.MAIN].update(utf8='[lod]\nenabled="false"\n'),
            lambda s: s['configuration_files']['files'].update({live.MAIN: record(s['configuration_files']['files'][live.MAIN]['utf8'].replace('near = 32', 'near = 33'))}),
            lambda s: s['configuration_files']['files'].update({'config/unrecognized.cfg': record('changed')}),
            lambda s: s['configuration_files']['files'].update({live.MAIN: record(s['configuration_files']['files'][live.MAIN]['utf8'] + '\n[unknown]\nkeep = true\n')})]
        for mutate in mutations:
            report, receipt = fixture_report(); state = report['phases'][0]['inputEnd']; mutate(state)
            with self.assertRaises((ValueError, TypeError)):
                rehash_state(state); live.analyze_report(report, receipt)

    def test_only_persisted_boolean_may_lag_live_global(self):
        report, receipt = fixture_report()
        for phase in report['phases']:
            state = phase['inputEnd']
            text = state['configuration_files']['files'][live.MAIN]['utf8']
            text = text.replace('enabled = ' + json.dumps(phase['enabled']), 'enabled = ' + json.dumps(not phase['enabled']), 1)
            state['configuration_files']['files'][live.MAIN] = record(text)
            rehash_state(state)
        result = live.analyze_report(report, receipt)
        self.assertEqual(result['phases'][0]['persisted_global_start_end'], [False, True])
        report['phases'][0]['inputEnd']['loaded_semantic_config']['lod']['enabled'] = True
        with self.assertRaises(ValueError): live.analyze_report(report, receipt)

    def test_effect_snapshot_gson_shape_and_all_inherit_protection(self):
        for mutate in (lambda s: s['live_pixel_effect_controls']['VOLUMETRICS'].update(mode='ADAPTIVE'),
            lambda s: s['live_pixel_effect_controls']['SHADOW_FILTERING']['policy'].update(farWorkRatio=.5),
            lambda s: s['live_pixel_effect_controls']['REFLECTIONS'].update(repaired=True)):
            report, receipt = fixture_report(); state = report['phases'][0]['inputEnd']; mutate(state)
            state['loaded_semantic_config']['effects'] = copy.deepcopy(state['live_pixel_effect_controls']); rehash_state(state)
            with self.assertRaises(ValueError): live.analyze_report(report, receipt)

    def test_window_pose_weather_and_capture_transitions_rejected(self):
        for mutate in (lambda s: s.update(day_time=6000), lambda s: s.update(yaw=90),
            lambda s: s.update(live_pixel_dh_present=True), lambda s: s.update(live_pixel_fov=71),
            lambda s: s.update(framebuffer_width=1488), lambda s: s['live_pixel_window_state'].update(focused='unknown'),
            lambda s: s['live_pixel_window_state'].update(focused='false'),
            lambda s: s['live_pixel_window_state']['pacing'].update(vsync=True)):
            report, receipt = fixture_report(); mutate(report['phases'][0]['inputEnd'])
            with self.assertRaises(ValueError): live.analyze_report(report, receipt)
        report, receipt = fixture_report(); report['phases'][0]['windowState']['capture']['transition_counts'] = {'focus': 1}
        with self.assertRaises(ValueError): live.analyze_report(report, receipt)

    def test_gpu_frames_epochs_scopes_counters_and_settling_rejected(self):
        mutations = [lambda p: p['gpu'].pop(), lambda p: p['gpu'][0].update(epoch=99),
            lambda p: p['gpu'][0].update(frameId=p['startFrame']), lambda p: p['gpu'][0].update(milliseconds=0),
            lambda p: p['gpu'][0].update(scope='deferred'), lambda p: p['gpu'][0].update(frameId=p['gpu'][1]['frameId']),
            lambda p: p.update(elapsedSeconds=19.99), lambda p: p.update(settleElapsedSeconds=7.99),
            lambda p: p['timingSnapshot'].update(pendingQueries=13),
            lambda p: p['timingSnapshot']['scopes']['game-render'].update(droppedQueries=1),
            lambda p: p['timingSnapshot'].update(scheduledSampledFrames=70),
            lambda p: p['timingSnapshot'].update(sampleInterval=16)]
        for mutate in mutations:
            report, receipt = fixture_report(); mutate(report['phases'][0])
            with self.assertRaises(ValueError): live.analyze_report(report, receipt)
        report, receipt = fixture_report(); report['phases'][1]['gpuEpoch'] = 1
        for sample in report['phases'][1]['gpu']: sample['epoch'] = 1
        with self.assertRaisesRegex(ValueError, 'reused'): live.analyze_report(report, receipt)

    def test_direct_artifacts_need_independent_complete_exact_inventory(self):
        mutations = [lambda r: r['loaded_artifact_evidence'].update(valid=False),
            lambda r: r['loaded_artifact_evidence']['loaded_artifact_inventory'].pop(),
            lambda r: r['loaded_mods'].update(unknown='1'),
            lambda r: r['loaded_artifact_inventory'][0].update(uri='file:/wrong.jar'),
            lambda r: r['loaded_artifacts_sha256'].update({'/extra.jar': 'f' * 64})]
        for mutate in mutations:
            report, receipt = fixture_report(); mutate(report)
            with self.assertRaises(ValueError): live.analyze_report(report, receipt)
        report, receipt = fixture_report(); receipt['loaded_artifact_inventory'][0]['identity'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'frozen receipt'): live.analyze_report(report, receipt)
        with self.assertRaises(ValueError): live.analyze_report(report, {})

    def test_transport_restoration_policy_and_probe_failure(self):
        for mutate in (lambda r: r.update(transport='effects'), lambda r: r.update(core_protocol=1),
            lambda r: r.update(raster_policy_proof=True), lambda r: r.update(restored_initial_enabled=False),
            lambda r: r.update(starting_order='ON/OFF/ON/OFF'),
            lambda r: r.update(initial_warmup_seconds=29.99),
            lambda r: r.update(cpu_requested=False),
            lambda r: r['instrumentation_properties'].update(pixelCoverage='true'),
            lambda r: r['instrumentation_properties'].update(pixelCoverageOnly='true'),
            lambda r: r['instrumentation_properties'].update(cloudSamplerProbe='true')):
            report, receipt = fixture_report(); mutate(report)
            with self.assertRaises(ValueError): live.analyze_report(report, receipt)


if __name__ == '__main__':
    unittest.main()
