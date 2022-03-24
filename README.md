# MfkFilter

Фильтр трафика данных от контроллеров MFK1500

Устанавливается как служба с помощью nssm.exe 
(nssm.exe install filter)

Path: java.exe
Startup directory: <путь к папке с jar файлом>
Arguments: -jar "MfkFilter.jar" "-remote=10.98.254.208" "-local=192.168.2.100"
