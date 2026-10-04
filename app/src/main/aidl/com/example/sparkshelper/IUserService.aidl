// IUserService.aidl
package com.example.sparkshelper;

interface IUserService {
    /**
     * 在 shell 进程执行一条命令，返回合并后的 stdout+stderr。
     * @param cmd 例如 "input tap 100 200"
     * @return 命令输出；错误以 "ERROR: ..." 返回
     */
    String exec(String cmd) = 0;

    /** 返回一个常量，用于校验 UserService 是否存活 */
    String ping() = 1;
}
