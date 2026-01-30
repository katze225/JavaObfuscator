# JavaObfuscator

Java obfuscator with Minecraft plugins supports. 
Based on [ColonialObfuscator](https://github.com/katze225/JavaObfuscator/wiki)

## Features

- **String Encryption** - Encrypts string constants with XOR-based encryption
- **Number Obfuscation** - Obfuscates integer, long, float, and double constants
- **Boolean Obfuscation** - Transforms boolean values with complex expressions
- **Flow Obfuscation** - Creates complex control flow patterns
- **Dispatcher** - Converts methods to use dispatcher pattern
- **Shuffle** - Randomizes order of fields and methods

## Obfuscation
Before:
```
         public boolean returnTrue() {
             return true;
         }

         public boolean returnFalse() {
             return false;
         }

         public void testConditions(int n, int n2, int n3) {
             if (n == 200) {
                 System.out.println("n is 200");
             }
             if (n2 == 500) {
                 System.out.println("test2 is 500");
             }
             if (n3 != -1) {
                 System.out.println("n2 is not -1");
             }
         }
     }
```

After:
```
public class Test {
         static char[] кГБфбдГТЕжкД_ЭнЮгЧЙйАнЗроорцПРяЖФИНшйюЮмкЯУмЇиЙГИЖтЪА;
         static int хщЮрГЕІирІвряРауГфПИцШИиРвздЮЖщЭЯРряюКбИ;

         public void testConditions(int n, int n2, int n3) {
             this.кГБфбдГТЕжкД_жЖЮШАкляіЙвВїиЩьЬюМШЧеФекаьхУтВмвщЩщювЫШ(
                 0x582A5BE7 ^ 0x582A508B,
                 new Object[]{n, n2, n3}
             );
         }

         private void кГБфбдГТЕжкД_ЧкязФвуТыжЦэфнХЗхЬчрЛТщптЩтюцКУниТЙРИЪГВ(int n, int n2, int n3) {
             if (n == (0x7361D762 ^ кГБфбдГТЕжкД_ещмСРУУалнГлВпоЫЬЭОХХоиуЫмєЇпкУцДоСзЕвСц(
                 -1308310402, 399363459, -1429195483, -712292576))) {
                 System.out.println(ЯУШЇюЙдыгЛшТпхеДчфЬкХйЕцюЩПкРБчГЖСэнІВжЙ(...));
             }
             if (n2 == (0x56F338AE ^ кГБфбдГТЕжкД_ещмСРУУалнГлВпоЫЬЭОХХоиуЫмєЇпкУцДоСзЕвСц(
                 174662077, -491941917, -674906218, -1928584349))) {
                 System.out.println(ЯУШЇюЙдыгЛшТпхеДчфЬкХйЕцюЩПкРБчГЖСэнІВжЙ(...));
             }
             if (n3 != (0xC0A86905 ^ кГБфбдГТЕжкД_ещмСРУУалнГлВпоЫЬЭОХХоиуЫмєЇпкУцДоСзЕвСц(...))) {
                 System.out.println(ЯУШЇюЙдыгЛшТпхеДчфЬкХйЕцюЩПкРБчГЖСэнІВжЙ(...));
             }
         }

         public boolean returnTrue() {
             return (Boolean)this.кГБфбдГТЕжкД_жЖЮШАкляіЙвВїиЩьЬюМШЧеФекаьхУтВмвщЩщювЫШ(
                 0x289DDE0C ^ 0x289D9FD7, new Object[0]
             );
         }

         public boolean returnFalse() {
             return (Boolean)this.кГБфбдГТЕжкД_жЖЮШАкляіЙвВїиЩьЬюМШЧеФекаьхУтВмвщЩщювЫШ(
                 0x3631CF9A ^ 0x3631D9A5, new Object[0]
             );
         }
     }
```

## How to run
Check out the [wiki](https://github.com/katze225/JavaObfuscator/wiki/Usage).

## Author

by @core2k21 (tg)
