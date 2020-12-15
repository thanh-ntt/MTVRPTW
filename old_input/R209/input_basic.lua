N	= 101;
Ca	= 200;
VFC	= 10000;
SV	= 750;
T	= 600;
Weight	= {1.0,1.0,1.0,1.0,3.0,3.0};
REP	= {2,4};  
-- э MaxCI
MaxChaos= {20,35};
Zincr	= 1.2;
Zdecr	= 0.8;
H       = 61;
MaxIter	= {100,1000};
Ts	= {1,3};
OTs	= {1,3};
MaxCCI  = {15,25};
P       = 0.3;
Arc     = 20;
GN      = 3;
M       = 5;
Method  = 1;  --1, ㄖ2, tabu3, Jr
TB_Num  = 8;
WT      = 5.0;
K       = 10.0;
EN      = 1;
bOpt    = true;


-------------------------
-- 7/27 穝糤把计

-- 氨ゎ兵ン羆Θセ
dbTotalCost = 22147;

-- ﹚竡ㄇ Range
R_Chaos      = {2, 50};
R_Iter       = {1500, 1500};
R_Ts         = {3, 39};
R_CCI        = {2,49};
R_M          = {3, 49};




--*************************
-- 程ㄎて隔畖把计

-- 琌虫诫ΩCN3*(CN1+CN2)Ω讽把计 false某CN1CN2 砞ex. MaxIter/2
bMultiProc4Opt = true;
CN1 = 5;
CN2 = 5;
CN3 = 2;