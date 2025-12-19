此目录下的文件是用来发布到sonatype时使用， 不能用于工程创建。

使用gpg生成sonatype秘钥:

1、 gpg --gen-key
2、 gpg --keyserver keys.openpgp.org --send-keys  你的公钥(一串十六进制的数字，例：DE346FA5)
      提示： gpg: 从公钥服务器接收失败：Server indicated a failure  表示服务器不可用
